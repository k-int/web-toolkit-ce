package com.k_int.web.toolkit.files
import java.sql.Blob

import jakarta.persistence.Lob

import org.hibernate.engine.jdbc.BlobProxy
import org.springframework.web.multipart.MultipartFile
import groovy.transform.CompileDynamic

import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity


@GrailsCompileStatic
@Entity
class LOBFileObject extends FileObject implements MultiTenant<LOBFileObject>, Clonable<LOBFileObject> {

  static cloneStaticValues = [
    fileContents: { ->
      cloneFileContents(delegate)
    }
  ]

  @CompileDynamic
  private static Blob cloneFileContents(Object instance) {
    def fileContents = instance.fileContents
    BlobProxy.generateProxy(fileContents.getBinaryStream(), fileContents.length())
  }
  
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
  }

  static mapping = {
    discriminator "DB"
  }
  
  @Override
  public LOBFileObject clone () {
    Clonable.super.clone()
  }
}
