package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class PropertyTypeBoolean extends CustomProperty<Boolean> implements MultiTenant<PropertyTypeBoolean> { 
  
  Boolean value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
}
