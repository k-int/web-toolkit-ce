package com.k_int.web.toolkit.files
import java.sql.Blob

import javax.persistence.Lob

import org.hibernate.engine.jdbc.BlobProxy
import org.springframework.web.multipart.MultipartFile
import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
@GrailsCompileStatic
class FileObject implements MultiTenant<FileObject> {

  String id
  FileUpload fileUpload
  
  static belongsTo = [fileUpload: FileUpload]
  
  @Lob
  Blob fileContents
    
  void setFileContents ( Blob fileContents ) {
    this.fileContents = fileContents
  }
  
  void setFileContents( InputStream is, long length ) {
    setFileContents( BlobProxy.generateProxy(is, length) )
  }
  
  void setFileContents( MultipartFile file ) {
    setFileContents( file.inputStream, file.size )
  }

  static constraints = {
    fileContents nullable: false
    fileUpload   nullable: false
  }

  static mapping = {
                  id column: 'fo_id', generator: 'uuid2', length: 36
         fileContent column: 'fo_contents'
  }
}
