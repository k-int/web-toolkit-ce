package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomPropertyDefinition
import com.k_int.web.toolkit.refdata.RefdataCategory

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

/**
 * Expands the definition class with a RefdataCategory to drive selection only
 */
class CustomPropertyRefdataDefinition extends CustomPropertyDefinition implements GormEntity<CustomPropertyDefinition>, MultiTenant<CustomPropertyDefinition> {
  RefdataCategory category
}
