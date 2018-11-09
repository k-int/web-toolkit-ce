package com.k_int.web.toolkit.refdata
import grails.gorm.MultiTenant

class RefdataCategory implements MultiTenant<RefdataCategory> {

  String id
  String desc

  static mapping = {
         id column: 'rdc_id', generator: 'uuid', length:36
    version column: 'rdc_version'
       desc column: 'rdc_description'
  }

  static constraints = {
  }
}
