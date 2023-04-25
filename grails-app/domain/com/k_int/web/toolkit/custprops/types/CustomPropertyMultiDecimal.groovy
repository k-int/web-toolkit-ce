package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.databinding.BindImmutably

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyMultiDecimal extends CustomProperty<Set<BigDecimal>> implements MultiTenant<CustomPropertyMultiDecimal>, Clonable<CustomPropertyMultiDecimal> { 
  
  @BindImmutably
  Set<BigDecimal> value = []
  
  static hasMany = [value: BigDecimal]
  
//  static mapping = {
//    value cascade: 'all-delete-orphan'
//  }
  
  static constraints = {
    value nullable: false
  }
  
  @Override
  public CustomPropertyMultiDecimal clone () {
    Clonable.super.clone()
  }
  
}
