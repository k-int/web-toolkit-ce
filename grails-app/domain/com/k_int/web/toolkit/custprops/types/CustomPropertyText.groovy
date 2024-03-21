package com.k_int.web.toolkit.custprops.types

import org.grails.datastore.gorm.GormEntity

import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.domain.traits.Clonable

import grails.gorm.MultiTenant

class CustomPropertyText extends CustomProperty<String> implements GormEntity<CustomProperty<String>>, MultiTenant<CustomProperty<String>>, Clonable<CustomPropertyText> { 
  
  String value
  
  static constraints = {
    value nullable: false, blank: false, type:'text'
  }
  
  @Override
  public CustomPropertyText clone () {
    Clonable.super.clone()
  }
}
