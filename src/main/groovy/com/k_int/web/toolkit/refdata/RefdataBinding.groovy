package com.k_int.web.toolkit.refdata

import com.k_int.web.toolkit.databinding.BindUsingWhenRef

import grails.util.GrailsNameUtils
import grails.web.databinding.DataBindingUtils

class RefdataBinding {
  public static RefdataValue refDataBinding (obj, propName, source, boolean isCollection) {

    def target = obj[propName]
    def data
    
    data = isCollection ? source : source[propName]

    // If the data is asking for null binding then ensure we return here.
    if (data == null) {
      return null
    }

    // Create a map of id and value
    if (data instanceof String) {
      data = [
        'id': data,
        'value': data
      ]
    }
    
    
    // Default to the original if not a collection
    RefdataValue val = !isCollection ? obj[propName] : null
    if (data) {
      // Found by Id or lookup by value.
      val = RefdataValue.read(data['id'])
      
      if (!val) {
        if (propName == 'values' && RefdataCategory.isAssignableFrom(obj.class)) {
          
          if (!obj.id) {
            final String norm_value = RefdataValue.normValue( data['label'] ?: data['value'] )
            val = new RefdataValue(
              label: data['label'],
              value: norm_value
            )
            obj.addToValues(val)
            
            // Ensure we return early.
            return val
          }
          
          // Refdata category values collection.
          val = RefdataValue.lookupOrCreate(obj, data['label'], data['value'])
          
        } else {
          val = ((obj."lookup${GrailsNameUtils.getClassName(propName)}"(data['value'])) ?: val)
        }
      }
    }
    
    if (val) {
      // Only allow editing of refdata when adding to the category.
      if (data instanceof Map && propName == 'values' && RefdataCategory.isAssignableFrom(obj.class)) {
        final def bind_data = data.subMap(['label'])
        DataBindingUtils.bindObjectToInstance(val, bind_data)
      }
      val.save(failOnError:true)
    }
    
    val
  }
}
