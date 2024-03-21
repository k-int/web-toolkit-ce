package com.k_int.web.toolkit.custprops.types

import org.grails.datastore.gorm.GormEntity

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.databinding.BindImmutably
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyMultiInteger extends CustomProperty<Set<BigInteger>> implements GormEntity<CustomProperty<Set<BigInteger>>>, MultiTenant<CustomProperty<Set<BigInteger>>>, Clonable<CustomPropertyMultiInteger> { 
  
  @BindImmutably(true)
  Set<BigInteger> value = []
  
  static hasMany = [value: BigInteger]
  
//  static mapping = {
//    value cascade: 'all-delete-orphan'
//  }
  
  static constraints = {
    value nullable: false
  }
  
  @Override
  public CustomPropertyMultiInteger clone () {
    Clonable.super.clone()
  }
  
}
