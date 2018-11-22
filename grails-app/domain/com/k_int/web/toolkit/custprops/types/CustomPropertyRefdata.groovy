package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.refdata.RefdataCategory
import com.k_int.web.toolkit.refdata.RefdataValue
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class CustomPropertyRefdata extends CustomProperty<RefdataValue> implements MultiTenant<CustomPropertyRefdata> {
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
  
}
