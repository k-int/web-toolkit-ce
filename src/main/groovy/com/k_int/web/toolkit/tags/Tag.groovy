package com.k_int.web.toolkit.tags

import org.grails.datastore.mapping.model.PersistentProperty
import org.hibernate.NonUniqueResultException
import com.k_int.web.toolkit.databinding.BindUsingWhenRef
import com.k_int.web.toolkit.utils.DomainUtils

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.web.databinding.DataBindingUtils


@BindUsingWhenRef(
  { obj, propName, source ->
    
//  if (Tag.isAssignableFrom(source.class) ) {
//    // Just return the source.
//    System.out.println "Already a tag ${source}"
//    return source
//  }
  
  System.out.println "Hitting the bind using for ${obj.class}.${propName}"
  
  // Initialize the data var as the property from the binding source.
  def data = source
  
  // If the data is asking for null binding then ensure we return here.
  if (data == null) {
    return null
  }
  final PersistentProperty pp = DomainUtils.resolveDomainClass(Tag)?.getIdentity()
  Tag match = null
  if (!(data instanceof Map)) {
    System.out.println "Data not map"
    // Might be just an ID. Check if type coming in matches defined ID type.
    System.out.println "Checking identifier type ${pp?.type} is assignable from ${data.class}"
    final boolean idMatch = pp?.type.isAssignableFrom(data.class)
    System.out.println "... result ${idMatch}"
    
    System.out.println "Checking identifier type ${data.class} is String or no ID match"
    final boolean normValueMatch = String.isAssignableFrom(data.class) || !idMatch
    System.out.println "... result ${normValueMatch}"
    
    // Assume a single value to match either the id or normValue.
    try {
      match = Tag.createCriteria().get {
        or {
          if (idMatch) {
            System.out.println "Query idEq ${data}"
            idEq (data.asType(pp.type))
          }
          if (normValueMatch) {
            System.out.println "Query normValue eq ${Tag.normalizeValue( Tag.cleanValue("${data}") )}"
            eq "normValue" , Tag.normalizeValue( Tag.cleanValue("${data}") )
          }
        }
      }
    } catch (NonUniqueResultException e) {
      match = null
    }
    
    // New tag. Assume this is the value.
    if (!match && String.isAssignableFrom(data.class)) {
      System.out.println "No match create one"
      match = new Tag()
      match.value = data
      match.save(failOnError:true)
    }
  } else {
    // Map
    System.out.println "Data is map"
    System.out.println "\t... ${data}"
    try {
      match = Tag.createCriteria().get {
        if (data[pp.name]) {
          System.out.println "Query idEq ${data[pp.name]}"
          idEq (data[pp.name].asType(pp.type))
          
        } else if (data['normValue']) {
          System.out.println "Query normValue eq ${data['normValue']}"
          eq "normValue" , "${data['normValue']}"
          
        } else if (data['value']) {
          System.out.println "Query normValue eq ${Tag.normalizeValue( Tag.cleanValue( data['value'] ) )}"
          eq "normValue" , Tag.normalizeValue( Tag.cleanValue( data['value'] ) )
        }
        
      }
    } catch (NonUniqueResultException e) {
      match = null
    }
    
    if (!match) {
      
      System.out.println "No match"
      
      
      System.out.println "New Tag"
      match = new Tag()
      if (DataBindingUtils.bindObjectToInstance(match, data) != null) {
        // Null out if errors.
        
        System.out.println "Binding of new tag failed"
        match = null
      }
      match.save(failOnError:true)
    }
  }
  
  System.out.println "Returning ${match}"
  match
}
)
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
