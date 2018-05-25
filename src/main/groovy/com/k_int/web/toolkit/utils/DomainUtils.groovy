package com.k_int.web.toolkit.utils

import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import com.k_int.web.toolkit.search.Searchable
import grails.core.GrailsApplication
import grails.core.GrailsControllerClass
import grails.rest.RestfulController
import grails.util.Holders
import groovy.transform.Memoized
import java.lang.reflect.Field

public class DomainUtils {
  
  
  private static GrailsApplication getGrailsApplication() {
    Holders.grailsApplication
  }
  
  private static MappingContext getMappingContext() {
    def mc = grailsApplication.mainContext.getBean('grailsDomainClassMappingContext')
    mc
  }
  
  
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
  public static PersistentEntity findDomainClass ( final String domainClassString ) {
    if (!domainClassString) return null
    
    mappingContext.getPersistentEntity(domainClassString) ?: mappingContext.getPersistentEntities().find({PersistentEntity pe -> pe.javaClass.simpleName.toLowerCase() == domainClassString.toLowerCase()})
  }
  
  public static PersistentEntity resolveDomainClass (final def target) {
    
    def dc = target
    
    // We can accept the Basic class representation
    if (dc != null && !PersistentEntity.isAssignableFrom(dc.class)) {
      
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
      def owner = type.javaClass
      String lastPropName
      def props = prop.split('\\.')
      
      final Map<String,Boolean> searchConfig = [
        value: true,
        filter: true,
        sort: true
      ]
      
      final Set<String> keys = [] + searchConfig.keySet()
      
      props.each { p ->
        lastPropName = p
        owner = type.javaClass
        PersistentProperty theProp  = type.getPropertyByName(p)
        type = (theProp instanceof Association) ? ((Association)theProp).associatedEntity : theProp.type
        
        
        // We should check the presence of search annotation. Check the class first.
        Searchable searchable = null
        
        // Field annotation
        Field classField = owner.getDeclaredField(p)
        if (classField?.isAnnotationPresent(Searchable)) {
          
          // Get the value of the annotation on the field.
          searchable = classField.getAnnotation(Searchable)
          
        } else  if (owner.isAnnotationPresent(Searchable)) {
          
          // Get the value of the annotation on the class.
          searchable = owner.getAnnotation(Searchable)
        }
        
        if (searchable) {
          
          // Merge the values in.
          keys.each { String key ->
            searchConfig[key] = searchConfig[key] && (searchable."${key}"())
          }
        }
      }
      
      searchConfig['search'] = searchConfig['value']
      searchConfig.remove('value')
  
      // Get the class for the type.
      final def desc = [
        "domain":  (type instanceof PersistentEntity || mappingContext.isPersistentEntity(type)),
        "type"  :  (type instanceof PersistentEntity ? type.javaClass : type),
        "owner" :  owner,
        "prop"  :  lastPropName
      ] + searchConfig
      
      desc
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
      PersistentEntity type = resolveDomainClass(target)
      def ctrls = grailsApplication.getArtefacts(ControllerArtefactHandler.TYPE)
      for (int i=0; i<ctrls.length && !match; i++ ) {
        if (RestfulController.class.isAssignableFrom(ctrls[i].clazz)) {
          // We should check the ocject type.
          def controller = grailsApplication.mainContext.getBean(ctrls[i].clazz)
          if (controller.resource == type.javaClass) {
            match = ctrls[i]
          }
        }
      }
      
    } catch (Exception e) { return null }
    
    match
  }
}
