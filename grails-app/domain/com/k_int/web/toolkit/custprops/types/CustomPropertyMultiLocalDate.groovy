package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.custprops.CustomPropertyMulti
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

import java.time.LocalDate

@Entity
@GrailsCompileStatic
class CustomPropertyMultiLocalDate extends CustomPropertyMulti<LocalDate> implements MultiTenant<CustomPropertyMultiLocalDate>, Clonable<CustomPropertyMultiLocalDate> { 
  
  Set<LocalDate> value = []
  
  static hasMany = [value: LocalDate]
  
  static mapping = {
    value cascade: 'all-delete-orphan'
  }
  
  static constraints = {
    value nullable: false
  }
  
  @Override
  public CustomPropertyMultiLocalDate clone () {
    Clonable.super.clone()
  }
  
}
