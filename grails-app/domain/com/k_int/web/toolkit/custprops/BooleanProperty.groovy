package com.k_int.web.toolkit.custprops

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
public class BooleanProperty extends Property<Boolean> implements MultiTenant<BooleanProperty> {
  Boolean value
}
