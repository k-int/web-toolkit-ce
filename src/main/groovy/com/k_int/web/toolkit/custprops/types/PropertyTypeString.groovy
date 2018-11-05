package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.PropertyInstance

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class PropertyTypeString extends PropertyInstance<String> implements  MultiTenant<PropertyTypeString> { 
  
  String value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
}
