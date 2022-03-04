package com.k_int.web.toolkit.files
import java.sql.Blob

import javax.persistence.Lob

import org.hibernate.engine.jdbc.BlobProxy
import org.springframework.web.multipart.MultipartFile

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import groovy.util.logging.Slf4j

@GrailsCompileStatic
@Entity
abstract class FileObject implements MultiTenant<FileObject> {

  String id
  FileUpload fileUpload
  
  static belongsTo = [fileUpload: FileUpload]
  
  static constraints = {
    fileUpload   nullable: false
  }

  static mapping = {
    // tablePerHierarchy true
    // discriminator "fo_engine"
    id column: 'fo_id', generator: 'uuid2', length: 36
  }
  
  public abstract FileObject clone();

}
