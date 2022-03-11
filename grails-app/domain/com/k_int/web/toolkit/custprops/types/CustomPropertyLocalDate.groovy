package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

import java.time.LocalDate

@Entity
class CustomPropertyLocalDate extends CustomProperty<LocalDate> implements MultiTenant<CustomPropertyLocalDate>, Clonable<CustomPropertyLocalDate> { 
  
  LocalDate value
  
  static constraints = {
    value nullable: false, blank: false
  }
  
  @Override
  public CustomPropertyLocalDate clone () {
    Clonable.super.clone()
  }
  
}
