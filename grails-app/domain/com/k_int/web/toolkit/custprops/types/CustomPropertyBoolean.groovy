package com.k_int.web.toolkit.custprops.types

import org.grails.datastore.gorm.GormEntity

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant

@GrailsCompileStatic
class CustomPropertyBoolean extends CustomProperty<Boolean> implements GormEntity<CustomProperty<Boolean>>, MultiTenant<CustomProperty<Boolean>>, Clonable<CustomPropertyBoolean>  { 
  
  Boolean value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
  @Override
  public CustomPropertyBoolean clone () {
    Clonable.super.clone()
  }
  
}
