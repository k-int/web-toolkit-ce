package com.k_int.web.toolkit.utils

import org.apache.commons.collections.map.LRUMap
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.omg.CORBA.INTERNAL

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.core.GrailsControllerClass
import grails.rest.RestfulController
import grails.util.Holders
import groovy.transform.Memoized
import groovy.util.logging.Slf4j

@Slf4j
public class DomainUtils {
  
  public static class InternalPropertyDefinition extends Serializable {
    boolean cacheable = false
    boolean searchable = true
    boolean filterable = true
    boolean sortable = true
    boolean domain = false
    String subQuery = null
    Class type
    Class owner
    String name
  }
  
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
        case {it instanceof GrailsClass} :
          dc = dc.clazz
        case {it instanceof Class} :
          dc = dc.name
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
   * Find domain classes that extend or implement another.
   */
  @Memoized(maxCacheSize=10)
  public static Set<PersistentEntity> findSubDomains(final def target) {
    
    if (target) {
      final def type = resolveDomainClass(target)?.javaClass
      if (type) {
        return mappingContext.getPersistentEntities().findAll { PersistentEntity pe ->
          type != pe.javaClass && type.isAssignableFrom(pe.javaClass)
        }
      }
    }
    
    // Default null
    null
  }
  
  /**
   * Return true if this domain object is a superclass of another.
   */
  @Memoized(maxCacheSize=5)
  public static boolean isSuperDomain(final def target) {
    
    target && (findSubDomains(target) ?: []).size() > 0
  }
    
  /**
   * Resolves the dot-notated property name starting from the target.
   * 
   * @param target The starting domain class
   * @param prop The property name
   * @param searchSubclasses Whether to search for domain classes that extend the target (useful for polymorphic queries)
   * @return definition of the property including the owning class, the type of the property as well as the property name (last part only).
   */  
  private final static Map<String, InternalPropertyDefinition> propertyDefCache = Collections.synchronizedMap(new LRUMap<String, InternalPropertyDefinition>(100))
  public static InternalPropertyDefinition resolveProperty ( final def target, final String prop, final boolean searchSubclasses = false) {
    
    InternalPropertyDefinition foundDef = null
    def type = resolveDomainClass(target)
        
    if (type) {
      final String key = "${type.name}:${prop}"
      if (propertyDefCache.containsKey(key)) {
        log.debug "Returning ${key} from cache."
        return propertyDefCache[key]
      }
      try {
        if (!(target && prop)) {
          return null
        }
    
        // Cycle through the properties to get to the end target.
        def owner = type.javaClass
        String lastPropName
        String[] props = prop.split('\\.')
        
        final Map<String,Boolean> searchConfig = [
          value: true,
          filter: true,
          sort: true
        ]
        
        for (int i=0; i<props.length; i++) {
          final String p = props[i]
          owner = type.javaClass
          InternalPropertyDefinition currentDef = propertyDefCache["${owner.name}:${p}"]
          
          if (!currentDef) {
            lastPropName = p
            
            if (owner.metaClass.respondsTo(owner, 'getPropertyDefByName', p.class)) {
              currentDef = owner.getPropertyDefByName( p )
  //            type = resolveDomainClass(currentDef?.type)
              
              currentDef.subQuery = (0..(i-1)).collect { props[it] }.join('.')
              return currentDef
            } 
            
            // Allow attempt to resolve none special field directly.
            if (!currentDef) {
              PersistentProperty theProp = type.getPropertyByName(p)
              
              // Special "class" property should be treated as string.
              if (p == 'class') {
                type = Class
              } else {
                type = (theProp instanceof Association) ? ((Association)theProp).associatedEntity : theProp.type
              }
            
              currentDef = new InternalPropertyDefinition()
              currentDef.cacheable = true
              currentDef.domain = (type instanceof PersistentEntity || mappingContext.isPersistentEntity(type))
              currentDef.type   = (type instanceof PersistentEntity ? type.javaClass : type)
              currentDef.owner  = owner
              currentDef.name   = lastPropName
              
              // We know we can cache the intermediate values.
              propertyDefCache["${owner.name}:${lastPropName}"] = currentDef
            }
          } else {
            // Need to set the type from the cached data.
            type = resolveDomainClass(currentDef?.type)
          }
          
          if (currentDef) foundDef = currentDef
        }
        
      } catch (Exception e) { foundDef = null }
      
      
      // If we haven't found anything, should we check Subclasses?
      if (!foundDef && searchSubclasses && isSuperDomain(target)) {
        // Get the extensions or implementors.
        final def subs = findSubDomains(target)
        
        // Recursively check for a def. End on first match. This assumes
        // That no subclass will redefine the type of a property.
        for (int i=0; !foundDef && i<subs.size(); i++) {
          
          foundDef = resolveProperty ( subs[i].javaClass.name, prop, false )
        }
        
        if (foundDef) {
          // This may be a way down the chain. We should keep checking down the tree to find the 'first'
          // time the property is seen.
          Class implementor = foundDef.owner.superclass

          if (implementor != Object && implementor != type.javaClass) {
            InternalPropertyDefinition superDef = resolveProperty ( implementor, prop, false )
            while (superDef && implementor != Object && implementor != type.javaClass) {
              foundDef = superDef
              implementor = implementor.superclass
              superDef = resolveProperty ( implementor, prop, false )
            }
          }
        }
      }
    
      // Cache any found
      if (foundDef && foundDef.cacheable) {
        propertyDefCache[key] = foundDef
      }
    }
    
    foundDef
  }
  
  @Memoized(maxCacheSize=200)
  public static boolean isDomainPropertyCollection(final def target, final String propertyName ) {
    
    PersistentEntity pe = resolveDomainClass(target)
    if (pe) {
      PersistentProperty prop = pe.getPropertyByName(propertyName)
      return Collection.class.isAssignableFrom(prop.type);
    }
    
    false
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
