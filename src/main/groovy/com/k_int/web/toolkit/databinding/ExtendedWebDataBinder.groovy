package com.k_int.web.toolkit.databinding

import grails.core.GrailsApplication
import grails.databinding.DataBindingSource
import grails.databinding.SimpleMapDataBindingSource
import grails.databinding.events.DataBindingListener
import grails.web.databinding.GrailsWebDataBinder
import groovy.transform.CompileStatic
import groovy.util.logging.Log4j


@Log4j
class ExtendedWebDataBinder extends GrailsWebDataBinder {

  protected static final String NEW_IDENTIFIER_VALUE = "__new__"

  ExtendedWebDataBinder(GrailsApplication grailsApplication) {
    super(grailsApplication)
    log.debug "Replaceing default grails databinder"
  }

  /**
   * Adding listener calls for property that is of type collection here.
   * @see grails.web.databinding.GrailsWebDataBinder#processProperty(java.lang.Object, groovy.lang.MetaProperty, java.lang.Object, grails.databinding.DataBindingSource, grails.databinding.events.DataBindingListener, java.lang.Object)
   */
  @Override
  @CompileStatic
  protected processProperty(obj, MetaProperty metaProperty, val, DataBindingSource source, DataBindingListener listener, errors) {
    if(Collection.isAssignableFrom(metaProperty.type)) {
      if (listener == null || listener.beforeBinding(obj, metaProperty.name, val, errors) != false) {
        super.processProperty(obj, metaProperty, val, source, listener, errors)
        listener?.afterBinding(obj, metaProperty.name, errors)
      }
    } else {
      // None collections should just call the super method.
      super.processProperty(obj, metaProperty, val, source, listener, errors)
    }
  }

  /**
   * Extend this section to allow none collection, Class typed, properties to be be set to a brand new object value instead of attempting an update.
   * @see grails.databinding.SimpleDataBinder#setPropertyValue(java.lang.Object, grails.databinding.DataBindingSource, groovy.lang.MetaProperty, java.lang.Object, grails.databinding.events.DataBindingListener, boolean)
   */
  @Override
  protected setPropertyValue(obj, DataBindingSource source, MetaProperty metaProperty, propertyValue, DataBindingListener listener, boolean convertCollectionElements) {

    // Grab the type that the property value is declared as.
    Class propertyType = metaProperty.getType()

    // Now grab the name.
    String propName = metaProperty.getName()
      
    if(propertyType == null || propertyType == Object) {
      propertyType = getField(obj.getClass(), propName)?.type ?: Object
    }

    if ( grailsApplication.isDomainClass(propertyType) ) {

      if (propertyValue instanceof Map) {
        if (!Collection.isAssignableFrom(propertyType)) {

          // If the id denotes a new object we still need to make sure we remove the id.
          if (identifierValueDenotesNewObject(propertyValue['id'])) {
            log.debug ("Need to create new '${propertyType.name}' value for property '${propName}' of ${obj}")
            propertyValue = new ExtendedSimpleMapDataBindingSource(propertyValue)
            
            if (obj[propName] != null) {

              log.debug ("Existing value, needs removing!")
  
              // Also, null out the existing value.
              obj[propName] = null
            }
          }
        }

      } else if (propertyValue instanceof SimpleMapDataBindingSource) {
        if (!Collection.isAssignableFrom(propertyType)) {
          
          // If the id denotes a new object we still need to make sure we remove the id.
          if (identifierValueDenotesNewObject(propertyValue.getPropertyValue('id'))) {
            log.debug ("Need to create new '${propertyType.name}' value for property '${propName}' of ${obj}")
            propertyValue = new ExtendedSimpleMapDataBindingSource(propertyValue)
            
            if (obj[propName] != null) {

              log.debug ("Existing value, needs removing!")
  
              // Also, null out the existing value.
              obj[propName] = null
            }
          }
        }
      }
    }

    // We can now just drop through to the default implementation, which should now create a new value for us.
    super.setPropertyValue(obj, source, metaProperty, propertyValue, listener, convertCollectionElements)
  }

  @CompileStatic
  public static boolean identifierValueDenotesNewObject (def idValue) {
    (NEW_IDENTIFIER_VALUE == ("${idValue ?: ''}").toString().toLowerCase())
  }
  
  protected def attemptConversion (Class typeToConvertTo, value) {
    this.convert(typeToConvertTo, value)
  }
  
  @Override
  protected convert(Class typeToConvertTo, value) {
    
    if (value == null) {
        return null
    }
    
    // Class is a special type
    if (typeToConvertTo instanceof Class && value instanceof String) {
      return Class.forName(value)
    }
    
    def persistentInstance
    if(isDomainClass(typeToConvertTo)) {
        persistentInstance = getPersistentInstance(typeToConvertTo, value)
    }
    persistentInstance ?: super.convert(typeToConvertTo, value)
  }

  //  /**
  //   * Extend to create new when ID == __new__
  //   * @see grails.web.databinding.GrailsWebDataBinder#getIdentifierValueFrom(java.lang.Object)
  //   */
  //  @Override
  //  @CompileStatic
  //  protected getIdentifierValueFrom(source) {
  //        def idValue = super.getIdentifierValueFrom(source)
  //        if (identifierValueDenotesNewObject(idValue)) {
  //          // We should return null.
  //          idValue = null
  //        }
  //        idValue
  //    }
}