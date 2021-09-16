package com.k_int.web.toolkit.files

import com.k_int.web.toolkit.domain.traits.Clonable
import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import groovy.util.logging.Slf4j

@Entity
@Slf4j
@GrailsCompileStatic
class S3FileObject extends FileObject implements MultiTenant<S3FileObject>, Clonable<S3FileObject> {

  String s3ref
    
  static constraints = {
    s3ref nullable: false
  }

  static mapping = {
         s3ref column: 'fo_s3ref'
  }
  
  @Override
  public S3FileObject clone () {
    Clonable.super.clone()
  }
}
