package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class CustomPropertyInteger extends CustomProperty<Integer> implements MultiTenant<CustomPropertyInteger> { 
  
  Integer value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
}
