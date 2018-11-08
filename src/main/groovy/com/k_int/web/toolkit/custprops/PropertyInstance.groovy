package com.k_int.web.toolkit.custprops

import com.k_int.web.toolkit.custprops.types.*

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback

@Entity
class PropertyInstance<T> implements MultiTenant<PropertyInstance> {
  
  String name
  String stringValue
  
  T value
  
  static mapping = {
    tablePerHierarchy false
  }
}
