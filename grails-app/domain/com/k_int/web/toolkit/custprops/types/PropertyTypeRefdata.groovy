package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.refdata.RefdataCategory
import com.k_int.web.toolkit.refdata.RefdataValue
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class PropertyTypeRefdata extends CustomProperty<RefdataValue> implements MultiTenant<PropertyTypeRefdata> {
  RefdataCategory category
  RefdataValue value
  
  static constraints = {
    category nullable: false, blank: false
    value nullable: false, blank: false
  }
  
}
