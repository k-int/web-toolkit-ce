package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class PropertyTypeContainer extends CustomProperty<Set<CustomProperty>> implements MultiTenant<PropertyTypeContainer> {
  
//  static transients = ['value']
  
  static hasMany = [
    value: CustomProperty
  ]
  
//  @Override
//  Set<PropertyInstance> getValue() {
//    Set<PropertyInstance> props = []
//    this.hasMany.each { String prop, Class type ->
//      if (PropertyInstance.isAssignableFrom(type)) {
//        props += this."${prop}"
//      }
//    }
//    props
//  }
}
