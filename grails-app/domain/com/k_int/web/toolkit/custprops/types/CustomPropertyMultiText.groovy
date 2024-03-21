package com.k_int.web.toolkit.custprops.types

import org.grails.datastore.gorm.GormEntity

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.databinding.BindImmutably
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyMultiText extends CustomProperty<Set<String>> implements GormEntity<CustomProperty<Set<String>>>, MultiTenant<CustomProperty<Set<String>>>, Clonable<CustomPropertyMultiText> { 
  
  @BindImmutably(true)
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