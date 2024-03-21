package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import org.grails.datastore.gorm.GormEntity

@GrailsCompileStatic
class CustomPropertyBlob extends CustomProperty<Byte[]> implements GormEntity<CustomProperty<Byte[]>>, MultiTenant<CustomProperty<Byte[]>>, Clonable<CustomPropertyBlob> { 
  
  Byte[] value
  
  static constraints = {
    value nullable: false, blank: false, type:'blob'
  }
  
  @Override
  public CustomPropertyBlob clone () {
    Clonable.super.clone()
  }
  
}
