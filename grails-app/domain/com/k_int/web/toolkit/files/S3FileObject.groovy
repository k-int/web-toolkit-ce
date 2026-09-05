package com.k_int.web.toolkit.files

import com.k_int.web.toolkit.domain.traits.Clonable
import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import groovy.util.logging.Slf4j

@GrailsCompileStatic
@Entity
class S3FileObject extends FileObject implements MultiTenant<S3FileObject>, Clonable<S3FileObject> {

  String s3ref
  StoredS3Object storedObject

  // Clonable's nested copy supplies an empty property selection. Preserve
  // physical ownership alongside the key even though legacy rows allow null.
  static cloneDefaultProperties = ['s3ref', 'storedObject']

  static constraints = {
    s3ref nullable: false
    storedObject nullable: true // Existing external deployments retain legacy reads.
  }

  static mapping = {
    discriminator "S3"
         s3ref column: 'fo_s3ref'
         storedObject column: 'fo_stored_s3_object_id'

  }

  def afterDelete() {
    if (storedObject?.id) {
      grails.util.Holders.applicationContext.getBean(StoredS3ObjectService)
        .cleanupAfterCommit(storedObject.id)
    }
  }
  
  @Override
  public S3FileObject clone () {
    Clonable.super.clone()
  }
}
