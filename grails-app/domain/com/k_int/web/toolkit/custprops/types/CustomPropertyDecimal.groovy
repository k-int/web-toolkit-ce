package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class CustomPropertyDecimal extends CustomProperty<BigDecimal> implements MultiTenant<CustomPropertyDecimal> { 
  
  BigDecimal value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
}
