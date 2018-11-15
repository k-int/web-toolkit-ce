package com.k_int.web.toolkit.refdata
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class RefdataCategory implements MultiTenant<RefdataCategory> {

  String id
  String desc
  Set<RefdataValue> values = []
  
  static hasMany = [
    values: RefdataValue
  ]
  
  static mappedBy = [
    values: 'owner'
  ]

  static mapping = {
         id column: 'rdc_id', generator: 'uuid', length:36
    version column: 'rdc_version'
       desc column: 'rdc_description'
  }
}