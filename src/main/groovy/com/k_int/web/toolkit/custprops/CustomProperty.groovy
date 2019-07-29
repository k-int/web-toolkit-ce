package com.k_int.web.toolkit.custprops

import com.k_int.web.toolkit.custprops.types.*

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback

@Entity
class CustomProperty<T> implements MultiTenant<CustomProperty> {
  CustomPropertyDefinition definition
  T value
  
  String note
  
  CustomPropertyContainer parent
  
  boolean internal = true
  
  static mappedBy = [
    "parent" : "value",
  ]
  
  static constraints = {
    parent nullable: true
    definition nullable: false
    note nullable: true, blank: false
  }
  
  static mapping = {
    tablePerHierarchy false
    note type: "text"
    sort "definition"
  }
}
