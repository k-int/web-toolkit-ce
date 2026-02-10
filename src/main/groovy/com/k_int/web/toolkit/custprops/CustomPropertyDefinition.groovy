package com.k_int.web.toolkit.custprops

import jakarta.persistence.Transient
import jakarta.validation.UnexpectedTypeException
import org.springframework.validation.Errors
import com.k_int.web.toolkit.databinding.BindUsingWhenRef
import com.k_int.web.toolkit.refdata.RefdataValue

import grails.compiler.GrailsCompileStatic
import grails.databinding.SimpleMapDataBindingSource
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.util.GrailsClassUtils
import grails.util.GrailsNameUtils
import grails.web.databinding.DataBindingUtils
import groovy.util.logging.Slf4j

@Slf4j
@Entity
@GrailsCompileStatic
class CustomPropertyDefinition implements MultiTenant<CustomPropertyDefinition> {
  
  static transients = ['propertyInstance']
  private static final String DEFINITION_PROPERTY = 'definitionClass'

  String id
  String name
  String label
  String description
  String ctx
  Class<? extends CustomProperty> type
  
  // Denotes primary. This is the primary sort, then weight, then id.
  boolean primary = false

  // Denotes whether a Custom property is settable or not. Retired custprops cannot be primary
  boolean retired = false
  
  // Used for ordering. Larger weight values sink.
  int weight = 0
  
  boolean defaultInternal = true
    
  private static String nameToLabel (String value) {
    // Strip double whitespace entries.
    return value?.trim().replaceAll(/([a-z0-9A-Z])([A-Z][a-z])/, '$1 $2')
  }
  
  def beforeValidate() {
    if (!this.label && this.name) {
      this.label = nameToLabel(this.name)
    }
  }
  
//  static hasMany = [
//    propertyInstances: CustomProperty
//  ]
//  
//  static mappedBy = [
//    propertyInstances: "definition"
//  ]
  
  static constraints = {
    name            (nullable: false, blank: false, unique: true)
    description     (nullable: true, blank: false)
    type            (bindable: false, nullable: false)
    label           (nullable: false, blank: false)
    ctx             (nullable: true)
    primary         (nullable: false, validator: { val, CustomPropertyDefinition obj, Errors errors ->
      if (val && obj.retired) {
        errors.rejectValue('primary', 'cannot.be.primary.and.retired')
      }
    })
    retired         (nullable: false)
  }

  static mapping = {
    tablePerHierarchy false
    id column: 'pd_id', generator: 'uuid', length:36
    name column: 'pd_name', index: 'td_name_idx'
    description column: 'pd_description', type: 'text'
    type column: 'pd_type', index: 'td_type_idx'
    label column: 'pd_label', index: 'td_label_idx'
    weight column: 'pd_weight', index: 'td_weight_idx'
    primary column: 'pd_primary', index: 'td_primary_idx'
    retired column: 'pd_retired', index: 'td_retired_idx'
    ctx column: 'pd_ctx'
    sort 'primary': 'asc', 'weight':'asc', 'id':'asc'
  }
  
  static CustomPropertyDefinition forType (final Class<? extends CustomProperty> type, final Map otherProps = [:]) {
    CustomPropertyDefinition definition = null
    if (type) {
      // Grab the class or default to this one.
      Class<? extends CustomPropertyDefinition> cpdc = GrailsClassUtils.getStaticFieldValue(type, DEFINITION_PROPERTY) ?: CustomPropertyDefinition
      definition = cpdc.newInstance()
      
      // Use the binder instead.
      DataBindingUtils.bindObjectToInstance(definition, new SimpleMapDataBindingSource( otherProps ))
      definition.type = type
    }
    definition
  }
  
  static CustomPropertyDefinition forType (final String type, final Map otherProps = [:]) {
    CustomPropertyDefinition definition = null
    try {
      final Class<? extends CustomProperty> typeClass = Class.forName(
        "${CustomProperty.class.package.name}.types.${CustomProperty.class.simpleName}${GrailsNameUtils.getClassName(type)}"
      )

      if (typeClass) {
        definition = forType(typeClass, otherProps)
      }

      definition
    } catch (ClassNotFoundException cnfe) {
      throw new CustomPropertyException("No class found for type: ${type}", CustomPropertyException.INVALID_TYPE, type)
    }
  }

  CustomProperty getPropertyInstance(Map<String, ?> extraProperties = [:]) {
    type?.newInstance(([internal: (this.defaultInternal)] as Map) + extraProperties + ([definition: this] as Map))
  }
}

