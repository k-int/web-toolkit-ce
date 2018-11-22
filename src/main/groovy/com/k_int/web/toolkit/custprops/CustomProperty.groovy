package com.k_int.web.toolkit.custprops

import com.k_int.web.toolkit.custprops.types.*

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback

@Entity
class CustomProperty<T> implements MultiTenant<CustomProperty> {
  
//  String name
  CustomPropertyDefinition definition
  T value
  
  CustomPropertyContainer parent
  
  static mappedBy = [
    "parent" : "value",
  ]
  
  static constraints = {
    parent nullable: true, blank: false
    definition nullable: false
  }
  
  static mapping = {
    tablePerHierarchy false
    sort "definition"
  }
}
