package com.k_int.web.toolkit.files

import org.springframework.web.multipart.MultipartFile

class FileUploadService {

  public static final String LOB_STORAGE_ENGINE='LOB';
  public static final String S3_STORAGE_ENGINE='S3';

  public FileUpload save(MultipartFile file) {
    return save(file, LOB_STORAGE_ENGINE);
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
    // https://www.baeldung.com/aws-s3-java
    throw new RuntimeException('not implemented')
  }

}
