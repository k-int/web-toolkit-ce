package com.k_int.web.toolkit.files

import org.springframework.web.multipart.MultipartFile
import com.k_int.web.toolkit.settings.AppSetting

import org.hibernate.Hibernate;

class FileUploadService {

  StoredS3ObjectService storedS3ObjectService

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

    log.debug("FileUploadService::save(...,${storageEngine}) returning ${result}");

    return result;
  }

  private FileUpload LOBsave(MultipartFile file) {

    log.debug("LOBsave...");

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

  private FileObject s3FileObjectFromStream(String objectKey, InputStream stream, long size, long partSize) {
    storedS3ObjectService.upload(objectKey, stream, size, partSize)
  }

  private FileUpload S3save(MultipartFile file) {
    FileUpload.withTransaction { status ->
      try {
        String prefix = AppSetting.getSettingValue('fileStorage', 'S3ObjectPrefix') ?: ''
        String key = "${prefix}${UUID.randomUUID()}-${file.originalFilename}"
        FileObject object = file.inputStream.withCloseable { stream ->
          s3FileObjectFromStream(key, stream, file.size, -1)
        }
        FileUpload upload = new FileUpload(fileContentType: file.contentType,
          fileName: file.originalFilename, fileSize: file.size, fileObject: object)
        upload.save(flush: true, failOnError: true)
        upload
      } catch (Exception failure) {
        status.setRollbackOnly()
        log.error('S3 upload failed ({})', failure.class.simpleName)
        null
      }
    }
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
    storedS3ObjectService.configured()
  }

  /**
   * Return the inputStream for the given S3FileObject so we can stream the contents to a user
   */
  private InputStream getS3FileStream(S3FileObject file) {
    storedS3ObjectService.read(file)
  }

  private InputStream getInputStreamFor(FileObject fo) {

    InputStream result = null;

    if ( fo != null ) {
      if ( S3FileObject.isAssignableFrom(fo.class) ) {
        S3FileObject s3_file_object = (S3FileObject) Hibernate.unproxy(fo);
        result = getS3FileStream(s3_file_object);
      }
      else if ( LOBFileObject.isAssignableFrom(fo.class) ) {
        LOBFileObject lob_file_object = (LOBFileObject) Hibernate.unproxy(fo)
        result = lob_file_object.fileContents.binaryStream
      }
      else {
        throw new RuntimeException("Unknown class for file object: ${fo?.class?.name}");
      }
    }

    return result;
  }
}
