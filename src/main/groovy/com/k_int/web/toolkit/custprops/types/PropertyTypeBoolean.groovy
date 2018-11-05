package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.PropertyInstance

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class PropertyTypeBoolean extends PropertyInstance<Boolean> implements MultiTenant<PropertyTypeBoolean> { 
  
  Boolean value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
}
