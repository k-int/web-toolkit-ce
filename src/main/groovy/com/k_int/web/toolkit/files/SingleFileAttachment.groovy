package com.k_int.web.toolkit.files

import grails.gorm.MultiTenant
import grails.gorm.dirty.checking.DirtyCheck
import grails.gorm.multitenancy.Tenants

abstract class SingleFileAttachment implements MultiTenant<SingleFileAttachment> {
  
  // Add transient property for flagging file removal. Transients are ignored by the persistence
  // layer.
  
  String id
  FileUpload fileUpload
  static hasOne = [fileUpload: FileUpload]
  static mappedBy = [fileUpload: 'owner']
  
  static mapping = {
    tablePerHierarchy false
    id generator: 'uuid2', length:36
    fileUpload cascade: 'all'
  }
  
  static constraints = {
    fileUpload nullable: true
  }
}
