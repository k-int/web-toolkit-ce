package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

class CustomPropertyText extends CustomProperty<String> implements MultiTenant<CustomPropertyText>, Clonable<CustomPropertyText> { 
  
  String value
  
  static constraints = {
    value nullable: false, blank: false, type:'text'
  }
  
  @Override
  public CustomPropertyText clone () {
    Clonable.super.clone()
  }
}
