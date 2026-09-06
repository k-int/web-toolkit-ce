package com.k_int.web.toolkit.files
import java.sql.Blob

import jakarta.persistence.Lob

import org.hibernate.engine.jdbc.BlobProxy
import org.springframework.web.multipart.MultipartFile
import groovy.transform.CompileDynamic
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity


@GrailsCompileStatic
@Entity
class LOBFileObject extends FileObject implements MultiTenant<LOBFileObject>, Clonable<LOBFileObject> {

  static cloneStaticValues = [
    fileContents: { Object target ->
      // Clonable rehydrates with the source as owner and the new object as
      // delegate, then passes the target argument. Read bytes from the source.
      cloneFileContents(owner)
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
    // GORM does not instrument every overloaded setter. Explicitly mark the
    // Blob path so replacement is persisted and its old OID can be released.
    markDirty('fileContents')
    this.fileContents = fileContents
  }
  
  void setFileContents( InputStream is, long length ) {
    setFileContents( BlobProxy.generateProxy(is, length) )
  }
  
  void setFileContents( MultipartFile file ) {
    // Real servlet multipart streams need not support reset. Hibernate can bind
    // the same Blob again at flush/commit; reopen its source instead of buffering.
    ReopenableMultipartInputStream stream = new ReopenableMultipartInputStream(file)
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override void afterCompletion(int status) { stream.close() }
      })
    }
    setFileContents(stream, file.size)
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
