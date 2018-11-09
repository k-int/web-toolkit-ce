package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomProperty

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class PropertyTypeContainer extends CustomProperty<Set<CustomProperty>> implements MultiTenant<PropertyTypeContainer> {
  
  Set<CustomProperty> value
  static hasMany = [
    value: CustomProperty
  ]
}
