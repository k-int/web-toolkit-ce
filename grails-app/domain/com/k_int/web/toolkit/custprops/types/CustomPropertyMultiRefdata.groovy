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
class CustomPropertyMultiRefdata extends CustomPropertyMulti<RefdataValue> implements MultiTenant<CustomPropertyMultiRefdata>, Clonable<CustomPropertyMultiRefdata> { 
  
  Set<RefdataValue> value = []
  
  static hasMany = [value: RefdataValue]

  // Customise the definition class.
  static Class definitionClass = CustomPropertyRefdataDefinition
  
  RefdataValue lookupValue ( final String refDataValueString ) {
    def catId = definition.refresh().category
    RefdataValue.findByValueAndOwner( RefdataValue.normValue(refDataValueString), catId )
  }
  
  static mapping = {
    value cascade: 'all-delete-orphan'
  }
  
  static constraints = {
    value nullable: false
  }
  
  @Override
  public CustomPropertyMultiRefdata clone () {
    Clonable.super.clone()
  }
  
}