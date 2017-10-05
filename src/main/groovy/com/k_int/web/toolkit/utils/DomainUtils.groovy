package com.k_int.web.toolkit.utils

import com.fasterxml.jackson.databind.introspect.AnnotatedClass

import grails.core.GrailsApplication
import grails.core.GrailsControllerClass
import grails.core.GrailsDomainClass
import grails.rest.RestfulController
import grails.util.Holders
import groovy.transform.Memoized
import org.grails.core.artefact.ControllerArtefactHandler

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
  @Memoized(maxCacheSize=50)
  public static GrailsDomainClass findDomainClass ( final String domainClassString ) {
    if (!domainClassString) return null
    
    GrailsApplication grailsApplication = Holders.grailsApplication
    grailsApplication.getDomainClass("${domainClassString}") ?: grailsApplication.domainClasses.find { it.clazz.simpleName.toLowerCase() == domainClassString.toLowerCase() }
  }
  
  public static GrailsDomainClass resolveDomainClass (final def target) {
    
    def dc = target
    
    // We can accept the Basic class representation
    if (dc != null && !GrailsDomainClass.isAssignableFrom(dc.class)) {
      
      // Test the target.
      switch (dc) {
        case {it instanceof Class} :
          dc = target.name
        case {it instanceof String} :
          dc = findDomainClass (dc)
          break
        default:
          // Not resolvable
          dc = null
      }
    }
    
    dc
  }

  /**
   * Resolves the dot-notated property name starting from the target.
   * 
   * @param target The starting domain class
   * @param prop The property name
   * @return definition of the property including the owning class, the type of the property as well as the property name (last part only).
   */  
  @Memoized(maxCacheSize=500)
  public static def resolveProperty ( final def target, final String prop ) {

    try {
      if (!(target && prop)) {
        return null
      }
      
      def type = resolveDomainClass(target)
  
      // Cycle through the properties to get to the end target.
      def owner = type.clazz
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
  
  @Memoized(maxCacheSize=200)
  public static def findControllersForDomain ( final def target ) {
    if (!(target)) {
      return null
    }
    
    GrailsControllerClass match = null
    try {
      GrailsApplication grailsApplication = Holders.grailsApplication
      GrailsDomainClass type = resolveDomainClass(target)
      def ctrls = grailsApplication.getArtefacts(ControllerArtefactHandler.TYPE)
      for (int i=0; i<ctrls.length && !match; i++ ) {
        if (RestfulController.class.isAssignableFrom(ctrls[i].clazz)) {
          // We should check the ocject type.
          def controller = grailsApplication.mainContext.getBean(ctrls[i].clazz)
          if (controller.resource == type.clazz) {
            match = ctrls[i]
          }
        }
      }
      
    } catch (Exception e) { return null }
    
    match
  }
}
