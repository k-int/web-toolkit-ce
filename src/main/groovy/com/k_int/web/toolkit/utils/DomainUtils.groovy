package com.k_int.web.toolkit.utils

import grails.core.GrailsApplication
import grails.core.GrailsDomainClass
import grails.util.Holders
import groovy.transform.Memoized

public class DomainUtils {
  /**
   * Matches domain classes by Fully qualified or simple name. i.e. com.k-int.MyClass or MyClass.
   * If multiple classes exist in different packages with the same simple-name then the first
   * match will be returned.
   *   
   * @param grailsApplication
   * @param domainClassString
   * @return The matching class or null if not found. 
   */
  public static GrailsDomainClass findDomainClass ( String domainClassString ) {
    if (!domainClassString) return null
    
    GrailsApplication grailsApplication = Holders.grailsApplication
    grailsApplication.getDomainClass("${domainClassString}") ?: grailsApplication.domainClasses.find { it.clazz.simpleName == domainClassString }
  }

  /**
   * Resolves the dot-notated property name starting from the target.
   * 
   * @param target The starting domain class
   * @param prop The property name
   * @return definition of the property including the owning class, the type of the property as well as the property name (last part only).
   */
  @Memoized(maxCacheSize=1000)
  public static def resolveProperty ( def target, String prop ) {

    try {
      if (!(target && prop)) {
        return null
      }
      
      // We can accept the Basic class representation
      if (!(target instanceof GrailsDomainClass)) {
        switch (target) {
          case {it instanceof Class} :
            target = target.name
          case {it instanceof String} :
            target = findDomainClass (target)
            break
          default:
            // Not a valid type..
            return null
        }
      }
  
      // Cycle through the properties to get to the end target.
      def type = target
      def owner = target.clazz
      String lastPropName
      def props = prop.split('\\.')
      props.each { p ->
        lastPropName = p
        owner = type.clazz
        def theProp  = type.getPersistentProperty(p)
        def domainRef = theProp.referencedDomainClass
        type = domainRef ? domainRef : theProp.referencedPropertyType
      }
  
      // Get the class for the type.
      return [
        "domain":  (type instanceof GrailsDomainClass),
        "type"  :  type instanceof GrailsDomainClass ? type.clazz : type,
        "owner" :  owner,
        "prop"  :  lastPropName
      ]
    } catch (Exception e) { return null }
  }
}
