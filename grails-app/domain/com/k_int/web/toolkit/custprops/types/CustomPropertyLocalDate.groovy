package com.k_int.web.toolkit.custprops.types

import java.time.LocalDate

import org.grails.datastore.gorm.GormEntity

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyLocalDate extends CustomProperty<LocalDate> implements GormEntity<CustomProperty<LocalDate>>, MultiTenant<CustomProperty<LocalDate>>, Clonable<CustomPropertyLocalDate> { 
  
  LocalDate value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
  @Override
  public CustomPropertyLocalDate clone () {
    Clonable.super.clone()
  }
  
}
