package com.k_int.web.toolkit.custprops

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class TextProperty extends Property<String> implements MultiTenant<TextProperty> {
  String value
}
