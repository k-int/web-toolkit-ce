package com.k_int.web.toolkit.custprops.types

import java.time.LocalDate

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyMultiLocalDate extends CustomProperty<Set<LocalDate>> implements MultiTenant<CustomPropertyMultiLocalDate>, Clonable<CustomPropertyMultiLocalDate> { 
  
  Set<LocalDate> value = []
  
  static hasMany = [value: LocalDate]
  
//  static mapping = {
//    value cascade: 'all-delete-orphan'
//  }
  
  static constraints = {
    value nullable: false
  }
  
  @Override
  public CustomPropertyMultiLocalDate clone () {
    Clonable.super.clone()
  }
  
}
