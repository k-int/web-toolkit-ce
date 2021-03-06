package com.k_int.web.toolkit.files
import java.sql.Blob

import javax.persistence.Lob

import org.hibernate.engine.jdbc.BlobProxy
import org.springframework.web.multipart.MultipartFile

import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import groovy.util.logging.Slf4j

@Entity
@Slf4j
@GrailsCompileStatic
class FileObject implements MultiTenant<FileObject>, Clonable<FileObject> {

  String id
  FileUpload fileUpload
  
  static belongsTo = [fileUpload: FileUpload]
  
  static cloneStaticValues = [
    fileContents: { 
      BlobProxy.generateProxy(owner.fileContents.getBinaryStream(), owner.fileContents.length()) 
    }
  ]
  
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
  
  @Override
  public FileObject clone () {
    Clonable.super.clone()
  }
}
