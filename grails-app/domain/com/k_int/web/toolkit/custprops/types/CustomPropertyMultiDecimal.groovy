package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.databinding.BindImmutably

import org.grails.datastore.gorm.GormEntity

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyMultiDecimal extends CustomProperty<Set<BigDecimal>> implements GormEntity<CustomProperty<Set<BigDecimal>>>, MultiTenant<CustomProperty<Set<BigDecimal>>>, Clonable<CustomPropertyMultiDecimal> { 
  
  @BindImmutably(true)
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
