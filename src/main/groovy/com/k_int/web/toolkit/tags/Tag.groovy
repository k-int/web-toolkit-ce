package com.k_int.web.toolkit.tags

import org.grails.datastore.mapping.model.PersistentProperty
import com.k_int.web.toolkit.databinding.BindUsingWhenRef
import com.k_int.web.toolkit.utils.DomainUtils

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.web.databinding.DataBindingUtils


/**
 * Tag entity that is used by the Taggable interface. Represents a tag added to any domain class.
 */
@BindUsingWhenRef({ obj, propName, source ->
  
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
    final Serializable castId = idMatch ? (data as Serializable).asType(pp.type) : null
    final String normValue = normValueMatch ? Tag.normalizeValue(Tag.cleanValue("${data}")) : null
    match = findUniqueByIdOrNormValue(castId, normValue)
    
    // New tag. Assume this is the value.
    if (!match && String.isAssignableFrom(data.class)) {
      match = new Tag()
      match.value = data
      match.save(failOnError:true)
    }
  } else {
    // Map
    final Serializable castId = data[pp.name] ? (data[pp.name] as Serializable).asType(pp.type) : null
    final String normValue = data['normValue']
      ? "${data['normValue']}"
      : (data['value'] ? Tag.normalizeValue(Tag.cleanValue(data['value'])) : null)
    match = findUniqueByIdOrNormValue(castId, normValue)
    
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

  private static Tag findUniqueByIdOrNormValue(final Serializable id, final String normValue) {
    final List<Tag> matches
    if (id != null && normValue != null) {
      matches = Tag.findAllByIdOrNormValue(id, normValue, [max: 2])
    } else if (id != null) {
      matches = Tag.findAllById(id, [max: 2])
    } else if (normValue != null) {
      matches = Tag.findAllByNormValue(normValue, [max: 2])
    } else {
      matches = []
    }
    matches?.size() == 1 ? matches[0] : null
  }
}
