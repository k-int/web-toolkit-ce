package com.k_int.web.toolkit.domain.traits

import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.OneToOne
import org.grails.datastore.mapping.model.types.ToMany
import org.slf4j.Logger

import com.k_int.web.toolkit.utils.GormUtils

import grails.util.GrailsClassUtils
import groovy.transform.CompileStatic
import groovy.transform.SelfType

/**
 * A trait for a domain class to implement to add the ability to create a clone of itself.
 * 
 * @author Steve Osguthorpe<steve.osguthorpe@k-int.com>
 * @since 3.7.0
 */

@SelfType(GormEntity)
@CompileStatic
trait Clonable<D> {
  public static final String FIELD_COPY_BY_CLONING = 'copyByCloning'
  public static final String FIELD_CLONE_STATIC_VALUES = 'cloneStaticValues'
  public static final String FIELD_CLONE_DEFAULT_PROPERTIES = 'cloneDefaultProperties'
  
  
  @CompileStatic
  private Logger getLog() {
    this.getAt('log') ? this.getAt('log') as Logger : null
  }
  
  /**
   * Create a clone of this object.
   * 
   * @param ignoreRequired whether persistent properties marked as not nullable are ignored.
   * @return A clone of this object
   */
  @Override
  public D clone(final boolean ignoreRequired = false) {
    clone(ignoreRequired, null)
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
    
    // Start with a fresh instance.
    final D cloned = this.class.newInstance()
    
    // Persistent entity
    PersistentEntity pe = GormUtils.currentGormEntity(this.class)
    
    // Default to all properties.
    final List<PersistentProperty> props = pe.persistentProperties
    final PersistentProperty idProp = pe.identity
    final Set<String> propertiesSet = [] + (propertiesToCopy == null ? props.findResults { it.name != idProp.name ? it.name : null } : propertiesToCopy) as Set
    getLog()?.debug "Properties to clone initially set to ${propertiesSet}"

    if (propertiesSet.size() == 0) {
      propertiesSet.addAll(getCloneDefaultProperties())
      getLog()?.debug "Properties to clone empty, using defaults: ${propertiesSet}"
    }
    
    // Add any properties that are required.
    if (!ignoreRequired) {
      for (PersistentProperty pp : props) {
        if (pp != idProp) {
          if (pp instanceof ToMany) {
            // For collections we should check the size.
            final Property mappedForm = pp.mapping.mappedForm
            if (mappedForm.minSize > 0 && !cloned[pp.name]) {
              getLog()?.warn "Adding ${pp.name} to list of properties to clone as it has a minimum size of ${mappedForm.minSize}, and no default supplied."
              // Add to the list.
              propertiesSet << pp.name
            }
          } else if (!pp.isNullable() && cloned[pp.name] == null) {
            getLog()?.warn "Adding ${pp.name} to list of properties to clone as it is a required property, and no default was supplied."
            // Add to the list.
            propertiesSet << pp.name
          }
        } else {
          getLog()?.debug "Property {pp.name} is the identifier property. Skipping"
        }
      }
    }
    
    final Map<String, Closure> statics = getCloneStaticValues()
    
    // Cycle through each property. And copy each one in turn.
    for ( final String prop : propertiesSet ) {
      Closure finalValue = null
      if (statics.containsKey(prop)) {
        getLog()?.debug "Static value supplied for ${prop}"
        finalValue = statics[prop]
      }
      
      cloneSingleProperty(cloned, prop, finalValue)
    }
    
    // Return the final product.
    cloned
  }
  
  private void cloneSingleProperty( final D to, final String propertyName, final Closure finalValue ) {
    
    try {
      
      if (finalValue) {
        getLog()?.debug "Setting property ${propertyName} to result of closure."
        to[propertyName] = finalValue.rehydrate(to, this, this).call(to)
        return
      }
     
      // Should this be a deep clone
      boolean deepClone = getCopiedByCloning().contains(propertyName)
      
      // get value
      def val = this.getAt(propertyName)
      
      if (val != null) {
        if (deepClone) {
        
          getLog()?.debug "deepClone Copying property ${propertyName}. It is an instance of ${val?.class?.name}"

          if (val instanceof Collection) {
            getLog()?.debug "${propertyName} is a collection"
            def values = val.collect({ it.clone() })
            if (to instanceof GormEntity) {
              getLog()?.debug "Using the addTo method"
              values.each {
                to.addTo(propertyName, it )
              }
            } else {
              to[propertyName] = values
            }
          } else {
            to[propertyName] = val.invokeMethod('clone', [])
          }
        } else {
        
          // Just copy it
          getLog()?.debug "Shallow copying property ${propertyName}. Will be reference if not primitive."
          if (val instanceof Collection) {
            getLog()?.debug "${propertyName} is a collection"
            def values = this.getAt(propertyName)
            if (to instanceof GormEntity) {
              getLog()?.debug "Using the addTo method for association"
              values.each {
                to.addTo(propertyName, it)
              }
            } else {
              getLog()?.debug "Cloning collection with same elements"
              to[propertyName] = values.collect() // New collection of same elements.
            }
          } else {
            
            to[propertyName] = val
          }
        }
      } else {
        getLog()?.debug "No value for ${propertyName}, skipping."
      }
    } catch ( ReadOnlyPropertyException e ) {
      getLog()?.warn "Attempting to set read only property failed"
    } catch ( Exception e ) {
      getLog()?.error "Error copying property ${propertyName}", e
    }
  }
  
  private Set<String> copyByCloning = null
  
  private Set<String> getCopiedByCloning () {
    if (copyByCloning == null) {
      copyByCloning = [] as Set<String>
      
      // We should also add any 1-1 or 1 to many type props.
      PersistentEntity pe = GormUtils.currentGormEntity( this.class )
      for (final PersistentProperty pp : pe.getPersistentProperties()) {
        if (pp instanceof OneToOne || pp instanceof OneToMany ) {
          
          Association ass = pp as Association
          // Force these to be copied by creating new value so that we don't accidentally
          // move ownership of something.
          if (ass.isBidirectional() && ass.isOwningSide()) {
            getLog()?.debug "Adding property ${pp} to copy by cloning as this property is a OneToX type."
            copyByCloning << pp.name
          } else {
            getLog()?.debug "property ${pp} OneToX type but is reference."
          }
        }
      }
      
      copyByCloning.addAll( GrailsClassUtils.getStaticPropertyValue(this.class, FIELD_COPY_BY_CLONING) as Set<String> ?: Collections.EMPTY_SET )
    }
    copyByCloning
  }
  
  private Map<String, Closure> getCloneStaticValues () {
    GrailsClassUtils.getStaticPropertyValue(this.class, FIELD_CLONE_STATIC_VALUES) as Map<String, Closure> ?: Collections.EMPTY_MAP
  }

  private Set<String> getCloneDefaultProperties () {
    GrailsClassUtils.getStaticPropertyValue(this.class, FIELD_CLONE_DEFAULT_PROPERTIES) as Set<String> ?: Collections.EMPTY_SET
  }
}
