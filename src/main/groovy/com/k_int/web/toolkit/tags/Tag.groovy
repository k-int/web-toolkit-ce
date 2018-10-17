package com.k_int.web.toolkit.tags

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
class Tag implements MultiTenant<Tag> {
  
  String value
}
