package com.k_int.web.toolkit.custprops

import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import com.k_int.web.toolkit.ValueConverterService
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
abstract class Property <T extends Serializable> implements MultiTenant<Property> {
  
  static TYPES = [
    "text": TextProperty,
    "boolean": BooleanProperty
  ]
  
  String name
  T value
  
  static mapping = {
    tablePerHierarchy false
  }
}
