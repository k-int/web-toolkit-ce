package com.k_int.web.toolkit.databinding

import static groovy.transform.TypeCheckingMode.SKIP

import java.lang.reflect.Field

import javax.persistence.ManyToOne

import org.grails.databinding.ClosureValueConverter
import org.grails.databinding.xml.GPathResultMap
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association

import com.k_int.web.toolkit.utils.ClassUtils
import com.k_int.web.toolkit.utils.DomainUtils

import grails.core.GrailsApplication
import grails.databinding.DataBindingSource
import grails.databinding.SimpleMapDataBindingSource
import grails.databinding.converters.ValueConverter
import grails.databinding.events.DataBindingListener
import grails.web.databinding.GrailsWebDataBinder
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Log4j


@Log4j
@CompileStatic
class ExtendedWebDataBinder extends GrailsWebDataBinder {

  protected static final String NEW_IDENTIFIER_VALUE = "__new__"

  ExtendedWebDataBinder(GrailsApplication grailsApplication) {
    super(grailsApplication)
    log.debug "Replaceing default grails databinder"
  }

  @Memoized(maxCacheSize=10)
  protected boolean shouldBindCollectionImmutably (Class c, String propName) {
    def field = getField(c, propName)
    if (field) {

      // Grab the type of the field and check for an annotation at class level.
      def annotation = field.getAnnotation(BindImmutably)
      if (annotation) {
        return annotation.value()
      }
    }

    false
  }
  
  @CompileStatic(SKIP)
  protected boolean processCollectionEntryAction (final obj, final String propertyName, final collectionEntry, Class collectionEntryTargetType) {
    
    boolean processed = false
    if (collectionEntryTargetType && collectionEntry && collectionEntry instanceof Map && collectionEntry['_delete']) {
      final PersistentProperty pId = DomainUtils.resolveDomainClass(collectionEntryTargetType)?.getIdentity()
      
      // We need an identifier.
      if (collectionEntry[pId.name]) {
        // Do the delete.
        def toRemove = collectionEntryTargetType.read(collectionEntry[pId.name])
        
        if (toRemove) {
          removeElementFromCollection (obj, propertyName, toRemove )
          processed = true
        }
      }
    }
    
    processed
  }
  
  protected removeElementFromCollection(obj, String propName, elementToRemove) {
    if (obj && elementToRemove) {
      // Set propertyValue's otherside value to null for bidirectional manyToOne relationships
      def domainClass = DomainUtils.resolveDomainClass(obj.class)
      if (domainClass != null) {
        def property = domainClass.getPropertyByName(propName)
        if (property != null && property instanceof Association) {
          Association association = ((Association)property)
          if (association.bidirectional) {
            def otherSide = association.inverseSide
            if (otherSide instanceof ManyToOne) {
              // Null it out.
              elementToRemove[otherSide.name] = null
            }
          }
        }
      }
      
      Collection coll = obj[propName] as Collection
      if (coll != null && coll.size() > 0) {
        def referencedType = getReferencedTypeForCollection propName, obj
        if (referencedType != null) {
          if (referencedType.isAssignableFrom(elementToRemove.class)) {
            coll.remove(elementToRemove)
          }
        }
      }
    }
  }

  protected processCollectionProperty(final obj, final MetaProperty metaProperty, final val, final DataBindingSource source, final DataBindingListener listener, final errors) {
    if (source.dataSourceAware) {
      
      def propertyType = metaProperty.type
      if(Collection.isAssignableFrom(propertyType)) {
        
        def propertyName = metaProperty.name
        
        // We know we need to bind additively.
        def referencedType = getReferencedTypeForCollection(propertyName, obj)
        if( referencedType ) {
          
          // Create a list value
          def listValue
          if(val instanceof List) {
  
            listValue = (List)val
  
          } else if(val instanceof GPathResultMap && ((GPathResultMap)val).size() == 1) {
            def mapValue = (GPathResultMap)val
            def valueInMap = mapValue[mapValue.keySet()[0]]
            if(valueInMap instanceof List) {
              listValue = (List)valueInMap
            } else {
              listValue = [valueInMap]
            }
          }
  
          // Providing we have an iterable list we should bind now.
          if(listValue != null) {
            def coll = initializeCollection obj, propertyName, propertyType
            final ValueConverter converter = getValueConverterForCollectionItems(obj, propertyName)
            
            def itemsWhichNeedBinding = []
            for ( def item : listValue ) {
              if (!processCollectionEntryAction(obj, propertyName, item, referencedType)) {
                
                def persistentInstance
                if (converter) {
                  
                  // Just pass the value to the converter.
                  persistentInstance = converter.convert(item)
                  if (persistentInstance) {
                    itemsWhichNeedBinding << persistentInstance
                  }
                } else {
                
                  if(isDomainClass(referencedType)) {
                    
                    if(item instanceof Map || item instanceof DataBindingSource) {
                      def idValue = getIdentifierValueFrom(item)
                      if(idValue != null) {
                        persistentInstance = getPersistentInstance(referencedType, idValue)
                        if(persistentInstance != null) {
                          DataBindingSource newBindingSource
                          if(item instanceof DataBindingSource) {
                            newBindingSource = (DataBindingSource)item
                          } else {
                            newBindingSource = new SimpleMapDataBindingSource((Map)item)
                          }
                          bind persistentInstance, newBindingSource, listener
                          itemsWhichNeedBinding << persistentInstance
                        }
                      }
                    }
                  }
                }
                // Defers to just the item.
                if(persistentInstance == null) {
                  itemsWhichNeedBinding << item
                }
              }
            }
            if(itemsWhichNeedBinding) {
              
              // Our default is to always add to the Collection. So we allow an annotation to revert back to the
              // old way of binding.
              if (shouldBindCollectionImmutably(obj.class, propertyName)) {
                
                // Return the super implementation with our conversions made.
                super.processProperty(obj, metaProperty, itemsWhichNeedBinding, source, listener, errors)
              } else {
              
                // Skip anything else and just attempt to add the objects here.
                for(item in itemsWhichNeedBinding) {
                  addElementToCollection obj, metaProperty.name, metaProperty.type, item, false
                }
              }
            }
          }
        }
      }
    }
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
        // Process the property here. If we couldn't then we should
        processCollectionProperty(obj, metaProperty, val, source, listener, errors)

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
    if ( isDomainClass(propertyType) ) {

      if (propertyValue instanceof Map) {
        if (!Collection.isAssignableFrom(propertyType)) {

          // If the id denotes a new object we still need to make sure we remove the id.
          if (identifierValueDenotesNewObject(propertyValue['id'])) {
            log.debug ("Need to create new '${propertyType.name}' value for property '${propName}' of ${obj}")
            propertyValue = new ExtendedSimpleMapDataBindingSource(propertyValue as Map)

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
    if (typeToConvertTo == Class && value instanceof String) {
      return Class.forName(value)
    }
    super.convert(typeToConvertTo, value)
  }
  
  @Override
  @CompileStatic
  protected Field getField(Class clazz, String fieldName) {
    ClassUtils.getField(clazz, fieldName)
  }

  @Override
  @CompileStatic
  protected ValueConverter getValueConverterForField(obj, String propName) {

    // Allow the normal per property annotation to take precedence
    ValueConverter converter = super.getValueConverterForField(obj, propName)
    if (!converter) {
      try {
        def field = getField(obj.getClass(), propName)
        if (field) {
          Class<?> theClass = field.type 
          if (theClass) {
            // Grab the type of the field and check for an annotation at class level.
            def annotation = theClass.getAnnotation(BindUsingWhenRef)
            
            // This annotation should be searched for on parents too.
            while (theClass != Object && !annotation) {
              theClass = theClass.getSuperclass()
              annotation = theClass.getAnnotation(BindUsingWhenRef)
            }
            
            if (annotation) {
  
              // Ensure that this is a closure that is passed in.
              def valueClass = annotation.value()
              if (Closure.isAssignableFrom(valueClass)) {
                Closure closure = (Closure)valueClass.newInstance(null, null)
  
                // Curry both the obj and the propertyName. Useful when we need to know the origin.
                converter = new ClosureValueConverter(converterClosure: closure.curry(obj, propName), targetType: field.type)
              }
            }
          }
        }
      } catch (Exception e) {
      }
    }
    converter
  }

  @CompileStatic
  protected ValueConverter getValueConverterForCollectionItems(obj, String propName) {
    ValueConverter converter = null
    try {

      // Grab the type of the field and check for an annotation at class level.
      Class typeClass = getReferencedTypeForCollection (propName, obj)
      def annotation = typeClass?.getAnnotation(BindUsingWhenRef)
      
      // This annotation should be searched for on parents too.
      while (typeClass != Object && !annotation) {
        typeClass = typeClass.getSuperclass()
        annotation = typeClass.getAnnotation(BindUsingWhenRef)
      }
        
      if (annotation) {

        // Ensure that this is a closure that is passed in.
        def valueClass = annotation.value()
        if (Closure.isAssignableFrom(valueClass)) {
          Closure closure = (Closure)valueClass.newInstance(null, null)

          // Curry both the obj and the propertyName. Useful when we need to know the origin.
          converter = new ClosureValueConverter(converterClosure: closure.curry(obj, propName), targetType: typeClass)
        }
      }
    } catch (Exception e) {
    }
    converter
  }
}