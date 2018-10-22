package com.k_int.web.toolkit.tags

import org.grails.datastore.mapping.model.PersistentProperty

import com.k_int.web.toolkit.utils.DomainUtils

import grails.databinding.BindUsing
import grails.databinding.SimpleMapDataBindingSource
import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.web.databinding.DataBindingUtils


@BindUsing({ obj, source ->
  
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
    match = Tag.createCriteria().get {
      or {
        if (idMatch) {
          idEq (data)
        }
        if (normValueMatch) {
          eq "normValue" , Tag.normalizeValue( Tag.cleanValue(data) )
        }
      }
    }
    // New tag. Assume this is the value.
    if (!match) {
      match = new Tag()
      match.value = data
    }
  } else {
    // Map
    match = Tag.createCriteria().get {
    
      if (data[pp]) {
        idEq (data[pp])
        
      } else if (data['normValue']) {
        eq "normValue" , data['normValue']
        
      } else if (data['value']) {
        eq "normValue" , Tag.normalizeValue( Tag.cleanValue( data['value'] ) )
      }
      
    }
    if (!match) {
      match = new Tag()
      if (DataBindingUtils.bindObjectToInstance(match, data) != null) {
        // Null out if errors.
        match = null
      }
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
    normValue (nullable: false, blank:false)
  }
}
