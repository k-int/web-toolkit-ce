package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomPropertiesBinder
import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.databinding.BindUsingWhenRef

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
@BindUsingWhenRef({ obj, String propName, source -> CustomPropertiesBinder.bind (obj, propName, source) })
class CustomPropertyContainer extends CustomProperty<Set<CustomProperty>> implements MultiTenant<CustomPropertyContainer> {
  Set<CustomProperty> value = []
  static hasMany = [
    value: CustomProperty
  ]
  
  static mapping = {
    value cascade: 'all-delete-orphan', sort: "definition"
  }
}
