package com.k_int.web.toolkit.files

import org.springframework.web.multipart.MultipartFile
import com.k_int.web.toolkit.settings.AppSetting

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

class FileUploadService {

  public static final String LOB_STORAGE_ENGINE='LOB';
  public static final String S3_STORAGE_ENGINE='S3';

  public FileUpload save(MultipartFile file) {
    // See if a default storage engine app-setting has been set
    String default_storage_engine = AppSetting.getSettingValue('fileStorage', 'storageEngine');
    // If so, save using that, default back to LOB storage engine
    return save(file, default_storage_engine ?: LOB_STORAGE_ENGINE);
  }

  public FileUpload save(MultipartFile file, String storageEngine) {

    FileUpload result = null;

    switch ( storageEngine ) {
      case 'S3':
        result = S3save(file)
        break;
      case 'LOB':
      default:
        result = LOBsave(file)
        break;
    }

    return result;
  }

  private FileUpload LOBsave(MultipartFile file) {
    // Create our object to house our file data.
    FileObject fobject = new LOBFileObject ()
    fobject.fileContents = file

    FileUpload fileUpload = new FileUpload()
    fileUpload.fileContentType = file.contentType
    fileUpload.fileName = file.originalFilename
    fileUpload.fileSize = file.size
    fileUpload.fileObject = fobject

    fileUpload.save(flush:true)
    fileUpload
  }

  // Create a FileObject from the given stream details
  private FileObject s3FileObjectFromStream(String object_key,
                                            InputStream is,
                                            long stream_size,
                                            long offset) {

    String s3_endpoint = AppSetting.getSettingValue('fileStorage', 'S3Endpoint');
    String s3_access_key = AppSetting.getSettingValue('fileStorage', 'S3AccessKey');
    String s3_secret_key = AppSetting.getSettingValue('fileStorage', 'S3SecretKey');
    String s3_bucket = AppSetting.getSettingValue('fileStorage', 'S3BucketName');

    log.debug("s3FileObjectFromStream ${s3_endpoint} ${s3_access_key} ${s3_secret_key} ${s3_bucket}");

    // Create a minioClient with the MinIO server playground, its access key and secret key.
    // See https://blogs.ashrithgn.com/spring-boot-uploading-and-downloading-file-from-minio-object-store/
    MinioClient minioClient =
          MinioClient.builder()
              .endpoint(s3_endpoint)
              .credentials(s3_access_key, s3_secret_key)
              .build();

     minioClient.putObject(
       PutObjectArgs.builder()
         .bucket(s3_bucket)
         .object(object_key)
         .stream(is, stream_size, offset)
         .build());

    FileObject fobject = new S3FileObject()
    fobject.s3ref=object_key

    return fobject
  }

  private FileUpload S3save(MultipartFile file) {

    FileUpload fileUpload = null;

    try {

      String object_uuid = java.util.UUID.randomUUID().toString()
      String s3_object_prefix = AppSetting.getSettingValue('fileStorage', 'S3ObjectPrefix');
      String object_key = "${s3_object_prefix?:''}${object_uuid}-${file.originalFilename}"

      FileObject fobject = s3FileObjectFromStream(object_key, file.getInputStream(), file.size, -1);
      fileUpload = new FileUpload()
      fileUpload.fileContentType = file.contentType
      fileUpload.fileName = file.originalFilename
      fileUpload.fileSize = file.size
      fileUpload.fileObject = fobject
  
      fileUpload.save(flush:true)
    }
    catch ( Exception e ) {
      log.error("Problem with S3 updload",e);
    }

    return fileUpload
  }


  // Take the identified file_upload and move it's storage engine
  public boolean migrate(FileUpload file_upload, String target_engine) {
    boolean result = true;
    switch ( target_engine ) {
      case LOB_STORAGE_ENGINE:
        if ( ! ( file_upload.fobject instanceof LOBFileObject ) ) {  // Don't migrate if it's already LOB
          throw new RuntimeException("Migration to LOB storage not implemented");
        }
        break;
      case S3_STORAGE_ENGINE:
        if ( ! ( file_upload.fobject instanceof S3FileObject ) ) { // Don't migrate if it's already S3

        }
        break;
    }
    return result;
  }

  public boolean migrateAtMost(int n, String from, String to) {

    List<FileUpload> list_to_migrate = null;
    Map meta_params = [:]
    if ( n > 0 ) {
      meta_params.max = n
    }
    switch ( from ) {
      case LOB_STORAGE_ENGINE:
        list_to_migrate = LOBFileObject.executeQuery('select l.fileUpload from LOBFileObject as l', [:], meta_params);
        break;
      case S3_STORAGE_ENGINE:
        list_to_migrate = LOBFileObject.executeQuery('select l.fileUpload from S3FileObject as l', [:], meta_params);
        break;
    }

    switch ( to ) { 
      case LOB_STORAGE_ENGINE:
        throw new RuntimeException("Migration TO LOB storage not implemented");
        break;
      case S3_STORAGE_ENGINE:
        String s3_object_prefix = AppSetting.getSettingValue('fileStorage', 'S3ObjectPrefix');

        // Firstly - check that S3 is configured properly
        if ( checkS3Configured() ) {
          list_to_migrate.each { file_object_to_migrate ->
  
            String object_uuid = java.util.UUID.randomUUID().toString()
            String object_key = "${s3_object_prefix?:''}${object_uuid}-${file_object_to_migrate.fileName}"
            log.debug("Migrate ${file_object_to_migrate} to S3: ${object_key}");
            log.debug("Create S3 object for LOB object size=${file_object_to_migrate.fileSize}");
            FileObject original = file_object_to_migrate.fileObject
            FileObject replacement = s3FileObjectFromStream(object_key, 
                                                            file_object_to_migrate.fileObject.fileContents.getBinaryStream(), 
                                                            file_object_to_migrate.fileSize, -1)
          
            if ( replacement ) {
              replacement.fileUpload = file_object_to_migrate;
              replacement.save(flush:true, failOnError:true);
              FileUpload.executeUpdate('update FileUpload set fileObject=:a where id=:b',[a:replacement, b:file_object_to_migrate.id]);
              FileObject.executeUpdate('delete from FileObject where id = :a',[a:original.id]);
            }
          }
        }
        break;
    }
  }
  
  private boolean checkS3Configured() {
    String s3_endpoint = AppSetting.getSettingValue('fileStorage', 'S3Endpoint');
    String s3_access_key = AppSetting.getSettingValue('fileStorage', 'S3AccessKey');
    String s3_secret_key = AppSetting.getSettingValue('fileStorage', 'S3SecretKey');
    String s3_bucket = AppSetting.getSettingValue('fileStorage', 'S3BucketName');

    return ( ( s3_endpoint != null ) &&
             ( s3_access_key != null ) &&
             ( s3_secret_key != null ) &&
             ( s3_bucket != null ) )
  }

  /**
   * Return the inputStream for the given S3FileObject so we can stream the contents to a user
   */
  private InputStream getS3FileStream(S3FileObject fo) {
    String s3_endpoint = AppSetting.getSettingValue('fileStorage', 'S3Endpoint');
    String s3_access_key = AppSetting.getSettingValue('fileStorage', 'S3AccessKey');
    String s3_secret_key = AppSetting.getSettingValue('fileStorage', 'S3SecretKey');
    String s3_bucket = AppSetting.getSettingValue('fileStorage', 'S3BucketName');

    // Create a minioClient with the MinIO server playground, its access key and secret key.
    // See https://blogs.ashrithgn.com/spring-boot-uploading-and-downloading-file-from-minio-object-store/
    MinioClient minioClient =
          MinioClient.builder()
              .endpoint(s3_endpoint)
              .credentials(s3_access_key, s3_secret_key)
              .build();

    return minioClient.getObject(s3_bucket, fi.s3ref)
  }

  private InputStream getInputStreamFor(FileObject fo) {

    InputStream result = null;

    if ( fo instanceof S3FileObject ) {
      result = getS3FileStream(fo);
    }
    else if ( fi instanceof LOBFileObject ) {
      result = fo.fileContents.binaryStream
    }

    return result;
  }
}
