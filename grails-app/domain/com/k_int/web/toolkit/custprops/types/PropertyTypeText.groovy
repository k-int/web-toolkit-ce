package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class PropertyTypeText extends CustomProperty<String> implements MultiTenant<PropertyTypeText> { 
  
  String value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
}
