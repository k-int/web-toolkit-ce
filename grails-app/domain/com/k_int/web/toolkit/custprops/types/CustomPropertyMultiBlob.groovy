package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.custprops.CustomPropertyMulti
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
@GrailsCompileStatic
class CustomPropertyMultiBlob extends CustomPropertyMulti<Byte[]> implements MultiTenant<CustomPropertyMultiBlob>, Clonable<CustomPropertyMultiBlob> { 
  
  Set<Byte[]> value = []
  
  static hasMany = [value: Byte[]]
  
  static mapping = {
    value cascade: 'all-delete-orphan'
  }
  
  static constraints = {
    value nullable: false
  }
  
  @Override
  public CustomPropertyMultiBlob clone () {
    Clonable.super.clone()
  }
  
}
