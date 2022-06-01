package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyMultiBlob extends CustomProperty<Set<Byte[]>> implements MultiTenant<CustomPropertyMultiBlob>, Clonable<CustomPropertyMultiBlob> { 
  
  Set<Byte[]> value = []
  
  static hasMany = [value: Byte[]]
  
//  static mapping = {
//    value cascade: 'all-delete-orphan'
//  }
  
  static constraints = {
    value nullable: false
  }
  
  @Override
  public CustomPropertyMultiBlob clone () {
    Clonable.super.clone()
  }
  
}
