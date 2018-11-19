package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class CustomPropertyBigDecimal extends CustomProperty<BigDecimal> implements MultiTenant<CustomPropertyBigDecimal> { 
  
  BigDecimal value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
}
