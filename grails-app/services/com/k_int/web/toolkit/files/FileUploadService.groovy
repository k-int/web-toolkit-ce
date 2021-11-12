package com.k_int.web.toolkit.files

import org.springframework.web.multipart.MultipartFile
import com.k_int.web.toolkit.settings.AppSetting

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

  private FileUpload S3save(MultipartFile file) {

    String s3_endpoint = AppSetting.getSettingValue('fileStorage', 'S3Endpoint');
    String s3_access_key = AppSetting.getSettingValue('fileStorage', 'S3AccessKey');
    String s3_secret_key = AppSetting.getSettingValue('fileStorage', 'S3SecretKey');
    String s3_bucket = AppSetting.getSettingValue('fileStorage', 'S3BucketName');

    log.debug("S3save ${s3_endpoint} ${s3_access_key} ${s3_secret_key} ${s3_bucket}");

    // https://docs.min.io/docs/java-client-quickstart-guide.html
    // MinioClient = getMinioClient()
    // if ( minio_client ) {
      // See if the S3 credentials check out and the bucket exists
      // if ( verifyBucket(AppSettings.getAppSetting('BucketName') ) {
        // https://www.baeldung.com/aws-s3-java
        // Fetch (And possibly cache) S3 settings needed
        // perform load
      //}
    //}
    // throw new RuntimeException('not implemented')
    return null
  }

}
