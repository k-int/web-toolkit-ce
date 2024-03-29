package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyDecimal extends CustomProperty<BigDecimal> implements MultiTenant<CustomPropertyDecimal>, Clonable<CustomPropertyDecimal> { 
  
  BigDecimal value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
  @Override
  public CustomPropertyDecimal clone () {
    Clonable.super.clone()
  }
}
