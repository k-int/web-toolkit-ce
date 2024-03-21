package com.k_int.web.toolkit.custprops.types

import org.grails.datastore.gorm.GormEntity

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyInteger extends CustomProperty<BigInteger> implements GormEntity<CustomProperty<BigInteger>>, MultiTenant<CustomProperty<BigInteger>>, Clonable<CustomPropertyInteger> { 
  
  BigInteger value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
  @Override
  public CustomPropertyInteger clone () {
    Clonable.super.clone()
  }
  
}
