package com.k_int.web.toolkit.custprops

import com.k_int.web.toolkit.custprops.types.CustomPropertyContainer

class CustomPropertiesBinder {

  // Add the binder here as a static so we can debug easily.
  static final CustomPropertyContainer bind ( final CustomPropertyContainer obj, Map source ) {
    // Default way to bind when using this trait. Data is treated as immutable. i.e. Any properties specified,
    // are bound as is and replace any existing values.

    /* Expected format :{
     *  "propertyDefName" : value,
     *  "propertyDefName2" : {
     *    "nestedPropertyName" : 23
     *  }
     * }
     */
    if (source && source.size() > 0) {
      
      // Each supplied property. We only allow predefined types.
      final Set<String> propertyNames = source.keySet()
  
      // Grab the defs present for all properties supplied.
      final Set<CustomPropertyDefinition> propDefs = []
      CustomPropertyDefinition.withNewSession {
        propDefs << CustomPropertyDefinition.createCriteria().list {
          'in' "name", propertyNames
        }
      }
  
      // Go through every property...
      for (CustomPropertyDefinition propDef: propDefs) {
        
        // If is container, then recursively call the parent method.
        if (propDef.type == CustomPropertyContainer) {
          
        }
        
        // Grab the values sent in for this property.
        // Either {id: 'someId', value: someValue, [_delete: true]}
        // OR  { value: someValue }
        def vals = source[propDef.name]
        
        // Ensure we have a collection.
        if (vals instanceof Map) {
          vals = [vals]
        }
        
        if (vals instanceof Collection) {
          for (Map<String, ?> val : vals) {
            // If we have an ID. Select it by id and has to also be of this type.
            CustomProperty theProp
            if (val.id) {
              theProp = CustomProperty.read(val.id)
            } else {
              // New property.
              theProp = propDef.getPropertyInstance()
            }
          }
        }
      }
  
  
      // Else bind the property
  
      // Property has single value.
  
      // Property has multiple values. i.e. Multiple instances of properties,
      // with the same def.
    }
    
    obj.refresh()
  }

}
