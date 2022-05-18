package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.custprops.CustomPropertyMulti
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
@GrailsCompileStatic
class CustomPropertyMultiInteger extends CustomPropertyMulti<BigInteger> implements MultiTenant<CustomPropertyMultiInteger>, Clonable<CustomPropertyMultiInteger> { 
  
  Set<BigInteger> value = []
  
  static hasMany = [value: BigInteger]
  
  static mapping = {
    value cascade: 'all-delete-orphan'
  }
  
  static constraints = {
    value nullable: false
  }
  
  @Override
  public CustomPropertyMultiInteger clone () {
    Clonable.super.clone()
  }
  
}
