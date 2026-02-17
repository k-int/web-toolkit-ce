package com.k_int.web.toolkit.custprops.types

import com.k_int.web.toolkit.custprops.CustomPropertiesBinder
import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.custprops.CustomPropertyDefinition
import com.k_int.web.toolkit.databinding.BindUsingWhenRef
import com.k_int.web.toolkit.domain.traits.Clonable
import com.k_int.web.toolkit.utils.DomainUtils.InternalPropertyDefinition

import grails.gorm.MultiTenant

//@Entity
@BindUsingWhenRef({ obj, String propName, source -> CustomPropertiesBinder.bind (obj, propName, source) })
class CustomPropertyContainer extends CustomProperty<Set<CustomProperty>> implements MultiTenant<CustomPropertyContainer>, Clonable<CustomPropertyContainer> {
  
  static copyByCloning = ['value']

  static cloneDefaultProperties = ['value']
  
  Set<CustomProperty> value = []
  static hasMany = [
    value: CustomProperty
  ]
  
  /**
   * Do the lookup join by returning ids 
   * @param property
   * @return
   */
  public static Map<String, Object> handleLookupViaSubquery ( final String property ) {
    String[] parts = property.split(/\./)
    
    // We know we should have at least 2 parts...
    if (parts.length < 2) {
      return null
    }
    InternalPropertyDefinition knownDef = null
    if (!knownDef) {
      knownDef = getPropertyDefByName(parts[0])
    }
    [
      targetType: knownDef.type,
      definitionName: parts[0]
    ]
  }
  
  public static InternalPropertyDefinition getPropertyDefByName ( final String name ) {
    final Class type = CustomPropertyDefinition.findByName( name )?.type
    
    if ( type ) {
      InternalPropertyDefinition d = new InternalPropertyDefinition ()
      
      d.type = type
      d.domain = true
      d.sortable = false
      d.name = name
      d.owner = this
      
      return d
    }
    
    null
  }
  
  static mapping = {
    value cascade: 'all-delete-orphan', sort: "definition"
  }
  
  static constraints = {
    parent nullable: true
    definition nullable: true // The root container can have a null definition.
    value validator: { Set<CustomProperty> val, obj ->
      boolean valid = true
      if (val == null || val.isEmpty()) {
        return valid
      }
      
      // Run for all properties to collect all error messages.
      for (CustomProperty prop : val) {
        valid = valid && prop.validate()
      }
      valid
    }
  }
  
  @Override
  public CustomPropertyContainer clone () {
    Clonable.super.clone()
  }
}
