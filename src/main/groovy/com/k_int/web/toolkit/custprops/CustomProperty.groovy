package com.k_int.web.toolkit.custprops

import org.grails.datastore.gorm.GormEntity

import com.k_int.web.toolkit.custprops.types.*

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback

@Entity
@GrailsCompileStatic
class CustomProperty<T> implements GormEntity<CustomProperty<T>>, MultiTenant<CustomProperty<T>> {
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
  
  static mapping = {
    tablePerHierarchy false
    note type: "text"
    publicNote type: 'text'
    sort "definition"
  }
}
