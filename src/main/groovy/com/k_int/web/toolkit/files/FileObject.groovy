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
abstract class FileObject implements MultiTenant<FileObject>, Clonable<FileObject> {

  String id
  FileUpload fileUpload
  
  static belongsTo = [fileUpload: FileUpload]
  
  static constraints = {
    fileUpload   nullable: false
  }

  static mapping = {
    tablePerHierarchy true
    discriminator "fo_engine"
    id column: 'fo_id', generator: 'uuid2', length: 36
  }
  
  @Override
  public FileObject clone () {
    Clonable.super.clone()
  }
}
