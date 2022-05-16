package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.custprops.CustomPropertyMulti
import com.k_int.web.toolkit.domain.traits.Clonable

import com.k_int.web.toolkit.refdata.RefdataCategory
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.compiler.GrailsCompileStatic
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
@GrailsCompileStatic
class CustomPropertyMultiText extends CustomPropertyMulti<String> implements MultiTenant<CustomPropertyMultiText>, Clonable<CustomPropertyMultiText> { 
  
  Set<String> value = []
  
  static hasMany = [value: String]
  
  static mapping = {
    value cascade: 'all-delete-orphan'
  }
  
  static constraints = {
    value nullable: false
  }
  
  @Override
  public CustomPropertyMultiText clone () {
    Clonable.super.clone()
  }
  
}