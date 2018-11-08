package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.PropertyInstance

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class PropertyTypeContainer extends PropertyInstance<Set<PropertyInstance>> implements MultiTenant<PropertyTypeContainer> {
  
//  static transients = ['value']
  
  static hasMany = [
    value: PropertyInstance
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
