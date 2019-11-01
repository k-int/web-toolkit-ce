package com.k_int.web.toolkit.domain.traits

import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.OneToOne
import org.grails.datastore.mapping.model.types.ToMany
import org.slf4j.Logger

import grails.util.GrailsClassUtils
import groovy.transform.SelfType

/**
 * A trait for a domain class to implement to add the ability to create a clone of itself.
 * 
 * @author Steve Osguthorpe<steve.osguthorpe@k-int.com>
 * @since 3.7.0
 */
@SelfType(GormEntity)
trait Clonable<D> {
  public static final String FIELD_COPY_BY_CLONING = 'copyByCloning'
  public static final String FIELD_CLONE_STATIC_VALUES = 'cloneStaticValues'
  
  private Logger getLog() {
    this['log'] ? this['log'] as Logger : null
  }
  
  /**
   * Create a clone of this object.
   * 
   * @param ignoreRequired whether persistent properties marked as not nullable are ignored.
   * @return A clone of this object
   */
  @Override
  public D clone() {
    clone(false, Collections.emptySet())
  }
  
  /**
   * Create a clone of this object.
   * 
   * @param ignoreRequired whether persistent properties marked as not nullable are ignored.
   * @return A clone of this object
   */
  public D clone(final boolean ignoreRequired) {
    clone(ignoreRequired, Collections.emptySet())
  }
  
  /**
   * By default all properties assumed to be copied without deep cloning and recursively calling the clone method
   *
   * @return A new object populated from the properties of this one.
   */
  public D clone( final boolean ignoreRequired = false, final String... propertiesToCopy) {
    clone( ignoreRequired, propertiesToCopy as Set<String> )
  }
  
  /**
   * This allows certain properties to be cloned in a deep recursive manor by
   * calling the clone method on those objects.
   * 
   * @see {@link #clone() clone}
   * @param associationsToClone Property names to recursively call clone on instead of copying by reference.
   * @return A new object populated from the properties of this one.
   */
  public D clone( final boolean ignoreRequired = false, final Iterable<String> propertiesToCopy ) {
    
    final List<String> processed = []
    
    // Start with a fresh instance.
    final D cloned = this.class.newInstance()
    
    // Default to all properties.
    Set<String> propertiesSet = [] + (propertiesToCopy ?: this.properties.keySet())
    
    // Add any properties that are required.
    if (!ignoreRequired) {
      for (PersistentProperty pp : currentGormEntity().persistentProperties) {
        if (pp instanceof ToMany) {
          // For collections we should check the size.
          final Property mappedForm = pp.mapping.mappedForm
          if (mappedForm.minSize > 0) {
            log?.warn "Adding ${pp.name} to list of properties to clone as it has a minimum size of ${mappedForm.minSize}"
            // Add to the list.
            propertiesSet << pp.name
          }
        } else if (!pp.isNullable()) {
          log?.warn "Adding ${pp.name} to list of properties to clone as it is a required property"
          // Add to the list.
          propertiesSet << pp.name
          
        } 
      }
    }
    
    // Cycle through each property. And copy each one in turn.
    for ( final String prop : propertiesSet ) {
      cloneSingleProperty(cloned, prop)
    }
    
    // Return the final product.
    cloned
  }
  
  private void cloneSingleProperty( final D to, final String propertyName ) {
    
    try {
      
      // Lets handle any values that have been defined as static values.
      final Map<String, Closure> statics = getCloneStaticValues()
      if (statics.containsKey(propertyName)) {
        log?.debug "Property ${FIELD_CLONE_STATIC_VALUES} contained value for ${propertyName}."
        to[propertyName] = statics.get(propertyName).rehydrate(to, this, this).call(to)
        return
      }
     
      // Should this be a deep clone
      boolean deepClone = getCopiedByCloning().contains(propertyName)
      
      // Is this property a none-persistent one.
  //    if (!pp) {
        if (this[propertyName]) {
          if (deepClone) {
          
            log?.debug "Copying property ${propertyName} by cloned value"
            if (this[propertyName] instanceof Collection) {
              log?.debug "${propertyName} is a collection"
              def values = this[propertyName].collect({ it.clone() })
              if (to instanceof GormEntity) {
                log?.debug "Using the addTo method"
                values.each {
                  to.addTo(propertyName, it.clone() )
                }
              } else {
                to[propertyName] = values
              }
            } else {
              to[propertyName] = this[propertyName].clone()
            }
          } else {
          
            // Just copy it
            log?.debug "Copying property ${propertyName} by reference"
            if (this[propertyName] instanceof Collection) {
              log?.debug "${propertyName} is a collection"
              def values = this[propertyName]
              if (to instanceof GormEntity) {
                log?.debug "Using the addTo method"
                values.each {
                  to.addTo(propertyName, it)
                }
              } else {
                to[propertyName] = values.collect() // New collection of same elements.
              }
            } else {
              to[propertyName] = this[propertyName]
            }
          }
        }
  //    }
    } catch ( ReadOnlyPropertyException e ) {
      log?.warn "Attempting to set read only property failed"
    } catch ( Exception e ) {
      log?.error "Error copying property ${propertyName}", e
    }
  }
  
  private Set<String> copyByCloning = null
  private Set<String> getCopiedByCloning () {
    if (copyByCloning == null) {
      copyByCloning = [] as Set<String>
      
      // We should also add any 1-1 or 1 to many type props.
      PersistentEntity pe = currentGormEntity()
      for (final PersistentProperty pp : pe.getPersistentProperties()) {
        if (pp instanceof OneToOne || pp instanceof OneToMany ) {
          
          Association ass = pp as Association
          // Force these to be copied by creating new value so that we don't accidentally
          // move ownership of something.
          if (ass.isBidirectional() && ass.isOwningSide()) {
            log?.debug "Adding property ${pp} to copy by cloning as this property is a OneToX type."
            copyByCloning << pp.name
          } else {
            log?.debug "property ${pp} OneToX type but is reference."
          }
        }
      }
      
      copyByCloning.addAll( GrailsClassUtils.getStaticPropertyValue(this.class, FIELD_COPY_BY_CLONING) as Set<String> ?: Collections.EMPTY_SET )
    }
    copyByCloning
  }
  
  
  private Map<String, ?> getCloneStaticValues () {
    GrailsClassUtils.getStaticPropertyValue(this.class, FIELD_CLONE_STATIC_VALUES) as Map<String, ?> ?: Collections.EMPTY_MAP
  }
  
  private static PersistentEntity currentGormEntity() {
    currentGormStaticApi().persistentEntity
  }
  
  private static GormStaticApi<D> currentGormStaticApi() {
    (GormStaticApi<D>)GormEnhancer.findStaticApi( this )
  }
}
