package com.k_int.web.toolkit.files

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@GrailsCompileStatic
@Entity
abstract class SingleFileAttachment implements MultiTenant<SingleFileAttachment> {
  
  // Add transient property for flagging file removal. Transients are ignored by the persistence
  // layer.
  
  String id
  FileUpload fileUpload
  static hasOne = [fileUpload: FileUpload]
  static mappedBy = [fileUpload: 'owner']
  
  public void setFileUpload(FileUpload fileUpload) {
    this.fileUpload = fileUpload
    
    if (fileUpload != null && fileUpload.owner?.id != this.id) {
      fileUpload.owner = this
    }
  }
  
  static mapping = {
    tablePerHierarchy false
    id generator: 'uuid2', length:36
    fileUpload cascade: 'all'
  }
  
  static constraints = {
    fileUpload nullable: true
  }
}
