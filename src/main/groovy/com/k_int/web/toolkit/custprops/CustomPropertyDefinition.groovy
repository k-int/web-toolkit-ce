package com.k_int.web.toolkit.custprops

import javax.persistence.Transient
import javax.validation.UnexpectedTypeException

import com.k_int.web.toolkit.refdata.RefdataValue

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.util.GrailsNameUtils
import groovy.util.logging.Log4j

@Log4j
@Entity
class CustomPropertyDefinition implements MultiTenant<CustomPropertyDefinition> {
  
  static transients = ['instance']

  String id
  String name
  String description
  Class<? extends CustomProperty> type
  
  static hasMany = [
    propertyInstances: CustomProperty 
  ]
  
  static mappedBy = [
    propertyInstances: "definition"
  ]
  
  static constraints = {
    name            (nullable: false, blank: false, unique:true)
    description     (nullable: true, blank: false)
    type            (nullable: false, blank: false)
  }

  static mapping = {
    id column: 'pd_id', generator: 'uuid', length:36
    name column: 'pd_name', index: 'td_name_idx'
    description column: 'pd_description'
    type column: 'pd_type', index: 'td_type_idx'
  }
  
  static CustomPropertyDefinition forType (final Class<? extends CustomProperty> type) {
    CustomPropertyDefinition definition = null
    if (type) {
      definition = new CustomPropertyDefinition("type" : (type))
    }
    definition
  }
  
  static CustomPropertyDefinition forType (final String type) {
    CustomPropertyDefinition definition = null
    final Class<? extends CustomProperty> typeClass = Class.forName("${CustomProperty.class.name}${GrailsNameUtils.getClassName(type)}")
    if (typeClass) {
      definition = new CustomPropertyDefinition("type" : (typeClass))
    }
    definition
  }

  CustomProperty getInstance() {
    CustomProperty inst = type?.newInstance()
    if (inst) {
      inst.definition = this
    }
    inst
  }
}

