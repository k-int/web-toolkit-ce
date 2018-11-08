package com.k_int.web.toolkit.tags

import org.grails.datastore.mapping.model.PersistentProperty
import org.hibernate.NonUniqueResultException
import com.k_int.web.toolkit.databinding.BindUsingWhenRef
import com.k_int.web.toolkit.utils.DomainUtils

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.web.databinding.DataBindingUtils


@BindUsingWhenRef({ obj, propName, source ->
    
//  if (Tag.isAssignableFrom(source.class) ) {
//    // Just return the source.
//    System.out.println "Already a tag ${source}"
//    return source
//  }
  
  // Initialize the data var as the property from the binding source.
  def data = source
  
  // If the data is asking for null binding then ensure we return here.
  if (data == null) {
    return null
  }
  final PersistentProperty pp = DomainUtils.resolveDomainClass(Tag)?.getIdentity()
  Tag match = null
  if (!(data instanceof Map)) {
    
    // Might be just an ID. Check if type coming in matches defined ID type.
    final boolean idMatch = pp?.type.isAssignableFrom(data.class)
    final boolean normValueMatch = String.isAssignableFrom(data.class) || !idMatch
    
    // Assume a single value to match either the id or normValue.
    try {
      match = Tag.createCriteria().get {
        or {
          if (idMatch) {
            idEq (data.asType(pp.type))
          }
          if (normValueMatch) {
            eq "normValue" , Tag.normalizeValue( Tag.cleanValue("${data}") )
          }
        }
      }
    } catch (NonUniqueResultException e) {
      match = null
    }
    
    // New tag. Assume this is the value.
    if (!match && String.isAssignableFrom(data.class)) {
      match = new Tag()
      match.value = data
      match.save(failOnError:true)
    }
  } else {
    // Map
    try {
      match = Tag.createCriteria().get {
        if (data[pp.name]) {
          idEq (data[pp.name].asType(pp.type))
          
        } else if (data['normValue']) {
          eq "normValue" , "${data['normValue']}"
          
        } else if (data['value']) {
          eq "normValue" , Tag.normalizeValue( Tag.cleanValue( data['value'] ) )
        }
        
      }
    } catch (NonUniqueResultException e) {
      match = null
    }
    
    if (!match) {
      
      match = new Tag()
      if (DataBindingUtils.bindObjectToInstance(match, data) != null) {
        // Null out if errors.
        match = null
      }
      match.save(failOnError:true)
    }
  }
  
  match
})
@Entity
class Tag implements MultiTenant<Tag> {
  
  String normValue
  String value
  
  private static String cleanValue (String value) {
    // Strip double whitespace entries.
    return value?.trim().replaceAll(/\s{2,}/, " ")
  }
  
  private static String normalizeValue (String value) {
    // Strip double whitespace entries.
    return value?.trim().replaceAll(/\s+/, "_").toLowerCase()
  }
  
  def beforeValidate() {
    this.value = cleanValue(this.value)
    this.normValue = normalizeValue(this.value)
  }
  
  static constraints = {
    value     (nullable: false, blank:false)
    normValue (nullable: false, blank:false, bindable: false)
  }
}
