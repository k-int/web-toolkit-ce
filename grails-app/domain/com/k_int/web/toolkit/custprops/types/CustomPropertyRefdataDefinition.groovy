package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomPropertyDefinition
import com.k_int.web.toolkit.refdata.RefdataCategory

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

/**
 * Expands the definition class with a RefdataCategory to drive selection only
 */
@Entity
class CustomPropertyRefdataDefinition extends CustomPropertyDefinition implements MultiTenant<CustomPropertyRefdataDefinition> {
  RefdataCategory category
}
