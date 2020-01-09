package com.k_int.web.toolkit.custprops.types

import org.hibernate.criterion.DetachedCriteria
import org.hibernate.criterion.Projections
import org.hibernate.criterion.Restrictions

import com.k_int.web.toolkit.custprops.CustomPropertiesBinder
import com.k_int.web.toolkit.custprops.CustomProperty
import com.k_int.web.toolkit.custprops.CustomPropertyDefinition
import com.k_int.web.toolkit.databinding.BindUsingWhenRef
import com.k_int.web.toolkit.utils.DomainUtils.InternalPropertyDefinition

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity

@Entity
@BindUsingWhenRef({ obj, String propName, source -> CustomPropertiesBinder.bind (obj, propName, source) })
class CustomPropertyContainer extends CustomProperty<Set<CustomProperty>> implements MultiTenant<CustomPropertyContainer> {
  Set<CustomProperty> value = []
  static hasMany = [
    value: CustomProperty
  ]
  
  /**
   * Do the lookup join by returning ids 
   * @param property
   * @return
   */
  public static DetachedCriteria handleLookupViaSubquery ( final String property ) {
    String[] parts = property.split(/\./)
    
    // We know we should have at least 2 parts...
    if (parts.length < 2) {
      return null
    }
    InternalPropertyDefinition knownDef = null
    if (!knownDef) {
      knownDef = getPropertyDefByName(parts[0])
    }
    // This criteria should operate on the targetted property but should return matching container id(s)
    new DetachedCriteria( knownDef.type.name )
      .createAlias('definition', 'definition')
      .add( Restrictions.eq('definition.name', parts[0]) )
    .setProjection( Projections.property('parent') )
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
  }
}
