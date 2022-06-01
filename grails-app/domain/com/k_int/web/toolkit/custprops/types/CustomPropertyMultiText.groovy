package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyMultiText extends CustomProperty<Set<String>> implements MultiTenant<CustomPropertyMultiText>, Clonable<CustomPropertyMultiText> { 
  
  Set<String> value = []
  
  static hasMany = [value: String]
  
//  static mapping = {
//    value cascade: 'all-delete-orphan'
//  }
  
  static constraints = {
    value nullable: false
  }
  
  @Override
  public CustomPropertyMultiText clone () {
    Clonable.super.clone()
  }
  
}