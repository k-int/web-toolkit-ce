package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class CustomPropertyBoolean extends CustomProperty<Boolean> implements MultiTenant<CustomPropertyBoolean>, Clonable<CustomPropertyBoolean>  { 
  
  Boolean value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
  @Override
  public CustomPropertyBoolean clone () {
    Clonable.super.clone()
  }
  
}
