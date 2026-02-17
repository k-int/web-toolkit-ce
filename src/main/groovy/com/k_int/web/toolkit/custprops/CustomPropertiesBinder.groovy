package com.k_int.web.toolkit.custprops

import com.k_int.web.toolkit.custprops.types.CustomPropertyContainer
import grails.databinding.SimpleMapDataBindingSource
import grails.util.Holders
import grails.web.databinding.DataBindingUtils
import grails.web.databinding.GrailsWebDataBinder
import groovy.util.logging.Log
import groovy.util.logging.Slf4j

@Slf4j
class CustomPropertiesBinder {
  
  private static GrailsWebDataBinder getDataBinder() {
    Holders.grailsApplication.mainContext.getBean(DataBindingUtils.DATA_BINDER_BEAN_NAME)
  }
  
  
  private static CustomPropertyContainer doBind (Map propSource, CustomPropertyContainer cpc) {
    log.debug ('Using custom binder')
    if (propSource && propSource.size() > 0) {
      // Each supplied property. We only allow predefined types.
      final Set<String> propertyNames = propSource.keySet()
      log.debug ("Looking for properties ${propertyNames}")
  
      // Grab the defs present for all properties supplied.
      final Set<CustomPropertyDefinition> propDefs =
        (CustomPropertyDefinition.findAllByNameInList(propertyNames as List<String>) ?: []) as Set<CustomPropertyDefinition>
      
      log.debug ("... found ${propDefs.size()}")
      // Go through every property...
      for (CustomPropertyDefinition propDef: propDefs) {
        
        log.debug ("Checking property ${propDef.name}")
        
        // Grab the values sent in for this property.
        // Either {id: 'someId', value: someValue, [_delete: true]} for update
        // OR  { value: someValue } for new.
        def vals = propSource[propDef.name]
        
        // If is container, then recursively call the parent method.
        if (propDef.type == CustomPropertyContainer) {
          
          // Add the container type property.
          cpc.addToValue ( doBind (vals, propDef.propertyInstance()) )
        }
        
        // Ensure we have a collection.
        if (vals instanceof Map) {
          vals = [vals]
        }
        
        if (vals instanceof Collection) {
          for (final def valObj : vals) {
            log.debug ("Attempting to bind ${valObj}")
            
            // Single values are presumed to be the 'value' key
            Map<String, ?> val
            if ( !(valObj instanceof Map) ) {
              val = [value: valObj]
            } else {
              val = valObj
            }
            log.debug ("Using shape ${val}")
            
            // If we have an ID. Select it by id and has to also be of this type.
            CustomProperty theProp
            final boolean deleteFlag = (val.'_delete' == true)
            if (val.id) {
              // Needs to be a get so that the changes are persisted.
              theProp = propDef.type.get(val.id)
              
              // If we are to delete the property we should do that here.
              if (deleteFlag) {
                cpc.removeFromValue ( theProp )
              }
            }
            
            // Not delete
            if (!deleteFlag) {
              // Create a new property if we need one.
              theProp = theProp ?: propDef.getPropertyInstance()
              log.debug ("Property instance to use as the target ${theProp}")
              
              dataBinder.bind(theProp, new SimpleMapDataBindingSource(val) )
              log.debug ("Property instance after binding ${theProp}")
              
              // Add the property to the container
              cpc.addToValue ( theProp )
            }
          }
        }
      }
  
  
      // Else bind the property
  
      // Property has single value.
  
      // Property has multiple values. i.e. Multiple instances of properties,
      // with the same def.
    }
    
    cpc.save(failOnError: true)
    cpc
  }

  // Add the binder here as a static so we can debug easily.
  static final CustomPropertyContainer bind ( obj, String propertyName, source ) {
    // Default way to bind when using this trait. Data is treated as immutable. i.e. Any properties specified,
    // are bound as is and replace any existing values.
    
    // We need the property source only
    def propSource = source[propertyName]
    doBind (propSource, (obj[propertyName] ?: new CustomPropertyContainer()))
    
  }

}
