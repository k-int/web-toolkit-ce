package com.k_int.web.toolkit.custprops.types

import java.time.LocalDate

import org.grails.datastore.gorm.GormEntity

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.databinding.BindImmutably
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyMultiLocalDate extends CustomProperty<Set<LocalDate>> implements GormEntity<CustomProperty<Set<LocalDate>>>, MultiTenant<CustomProperty<Set<LocalDate>>>, Clonable<CustomPropertyMultiLocalDate> { 
  
  @BindImmutably
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
