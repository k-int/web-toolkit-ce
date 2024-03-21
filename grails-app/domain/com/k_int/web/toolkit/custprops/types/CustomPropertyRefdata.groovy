package com.k_int.web.toolkit.custprops.types

import org.grails.datastore.gorm.GormEntity

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.gorm.MultiTenant


class CustomPropertyRefdata extends CustomProperty<RefdataValue> implements GormEntity<CustomProperty<RefdataValue>>, MultiTenant<CustomProperty<RefdataValue>>,Clonable<CustomPropertyRefdata> {
  RefdataValue value
  RefdataValue lookupValue ( final String refDataValueString ) {
    def catId = definition.refresh().category
    RefdataValue.findByValueAndOwner( RefdataValue.normValue(refDataValueString), catId )
  }
  
  // Customise the definition class.
  static Class definitionClass = CustomPropertyRefdataDefinition
  static constraints = {
    value nullable: false, blank: false
  }
  
  @Override
  public CustomPropertyRefdata clone () {
    Clonable.super.clone()
  }
  
}
