package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.custprops.CustomPropertyMulti
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
@GrailsCompileStatic
class CustomPropertyMultiDecimal extends CustomPropertyMulti<BigDecimal> implements MultiTenant<CustomPropertyMultiDecimal>, Clonable<CustomPropertyMultiDecimal> { 
  
  Set<BigDecimal> value = []
  
  static hasMany = [value: BigDecimal]
  
  static mapping = {
    value cascade: 'all-delete-orphan'
  }
  
  static constraints = {
    value nullable: false
  }
  
  @Override
  public CustomPropertyMultiDecimal clone () {
    Clonable.super.clone()
  }
  
}
