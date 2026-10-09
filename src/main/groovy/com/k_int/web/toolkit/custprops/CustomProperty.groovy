package com.k_int.web.toolkit.custprops

import com.k_int.web.toolkit.custprops.types.*

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback

@Entity
@GrailsCompileStatic
class CustomProperty<T> implements MultiTenant<CustomProperty> {
  CustomPropertyDefinition definition
  T value
  
  String note
  String publicNote
  
  CustomPropertyContainer parent
  
  boolean internal = true
  
  static mappedBy = [
    "parent" : "value",
  ]
  
  static constraints = {
    parent nullable: true
    definition nullable: false
    note nullable: true, blank: false
    publicNote nullable: true, blank: false
  }
  
  // An explicit delegate keeps inherited mapping calls independent of the subtype.
  static mapping = {
    delegate.tablePerHierarchy false
    delegate.note type: "text"
    delegate.publicNote type: 'text'
    delegate.sort "definition"
  }
}
