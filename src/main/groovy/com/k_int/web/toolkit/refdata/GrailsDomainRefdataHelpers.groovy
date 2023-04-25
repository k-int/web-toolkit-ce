package com.k_int.web.toolkit.refdata

import java.beans.PropertyDescriptor
import java.lang.annotation.Annotation
import java.lang.reflect.Field

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association

import com.k_int.web.toolkit.custprops.types.CustomPropertyRefdata
import com.k_int.web.toolkit.utils.DomainUtils

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.gorm.multitenancy.Tenants
import grails.util.GrailsClassUtils
import grails.util.GrailsNameUtils
import grails.util.Holders
import groovy.util.logging.Slf4j

@Slf4j
class GrailsDomainRefdataHelpers {
  
  
  /**
   * Checks to see if this field is annotated with the Category annotation and
   * uses it if present. Else it is derived from simple classname and property name.
   * 
   * @param c - The class to which the property belongs
   * @param propertyName - The name of the property to examine.
   * @return The derived or specified category name
   */
//  @Memoized(maxCacheSize=50)
  public static String getCategoryString(final Class<?> c, final String propertyName) {
    final String val = getFieldAnnotation(c, propertyName, CategoryId.class, true)?.value()?.trim()
    
    // Empty string tests FALSE in groovy truth
    val ? val : "${c.simpleName}.${GrailsNameUtils.getClassName(propertyName)}"
  }
  
  
  public static boolean getCategoryDefaultInternal(final Class<?> c, final String propertyName) {
    CategoryId cat = getFieldAnnotation(c, propertyName, CategoryId.class, true)
    cat ? cat.defaultInternal() : RefdataCategory.DEFAULT_INTERNAL
  }
  
  private static <T extends Annotation> T getFieldAnnotation(final Class<?> c, final String fieldName, Class<T> annotationClass, boolean searchParents = false) {
    T ann = null
    Field field = null
    try {
      field = c.getDeclaredField("${fieldName}")
      ann = field.getAnnotation(annotationClass)
      
    } catch (NoSuchFieldException e) { /* Silent */ }
    
    if (!field && searchParents) {
      Class<?> theClass = c.getSuperclass()
      while (theClass != Object && !field) {
        try {
          field = theClass.getDeclaredField("${fieldName}")
          ann = field.getAnnotation(annotationClass)
        } catch (NoSuchFieldException e) { 
          theClass = theClass.getSuperclass()
        }
      }
    }
    
    ann
  }
  
  public static void setDefaultsForTenant (final Serializable tid) {
    final GrailsApplication grailsApplication = Holders.grailsApplication
    final GrailsClass[] domainObj = grailsApplication.getArtefacts("Domain")
    Tenants.withId ( tid ) {
      domainObj.each { GrailsClass gc ->
        GrailsDomainRefdataHelpers.setDefaults(gc)
      }
    }
  }
  
  public static void setDefaults (final GrailsClass gc) {
    PersistentEntity pe = DomainUtils.resolveDomainClass(gc)
    if (pe) {
      setDefaults(pe)
    }
  }
  
  public static void setDefaults(PersistentEntity pe) {
    final Class<?> targetClass = pe.javaClass
    
    log.debug "Testing class ${targetClass.name} for refData defaults"
    
    if (!GrailsClassUtils.isAssignableOrConvertibleFrom(RefdataValue.class, targetClass)) {
      
      // Get the declared fields.
      for (PersistentProperty p : pe.getPersistentProperties()) {
        Class<?> type = p instanceof Association ? p.associatedEntity?.javaClass : null
        if (GrailsClassUtils.isAssignableOrConvertibleFrom(RefdataValue.class, type)) {
          
          // The field name.
          final String fn = p.name
          
          // Create our type string once per property.
          final String upperName = GrailsNameUtils.getClassName(fn)
          
          final String typeString = targetClass.metaClass.respondsTo(targetClass, "get${upperName}Category") ? targetClass."get${upperName}Category"() : null
          if (typeString) {            
            final boolean defaultInternal = targetClass.metaClass.respondsTo(targetClass, "get${upperName}DefaultInternal") ? targetClass."get${upperName}DefaultInternal"() : RefdataCategory.DEFAULT_INTERNAL
          
            log.debug "  Found property ${fn} that has compatible type"
            log.debug "  Category: ${typeString} (${defaultInternal ? 'internal' : 'none-internal'} if not present)"
            
            // Check for annotated values.
            Defaults defaults = getFieldAnnotation(targetClass, fn, Defaults.class)
            if (defaults) {
              log.debug "  Declared defaults :"
              defaults.value().each {
                log.debug "    ${it}"
                RefdataValue.lookupOrCreate(
                  typeString,
                  it,
                  null,
                  defaultInternal,
                  type
                )
              }
            }
          }
        }
      }
    }
  }

  public static void addMethods (GrailsClass gc) {
    PersistentEntity pe = DomainUtils.resolveDomainClass(gc)
    if (pe) {
      addMethods(pe)
    }
  }
  
  public static void addMethods (PersistentEntity pe) {
    // Excludes.
    final Set<Class<?>> excludes = [RefdataValue, RefdataCategory, CustomPropertyRefdata]
    
    final Class<?> targetClass = pe.javaClass
    final GrailsApplication grailsApplication = Holders.grailsApplication

    if ( !excludes.find { it.isAssignableFrom(targetClass) } ) {

      // Get each refdataValue type property (or derivative)
      PropertyDescriptor[] props = GrailsClassUtils.getPropertiesAssignableToType(targetClass, RefdataValue.class)
      for (final PropertyDescriptor pd : props) {

        Class<? extends RefdataValue> typeClass = pd.getPropertyType()

        // The ClassName representation of the property name.
        final String upperName = GrailsNameUtils.getClassName(pd.name)
        final String typeString = GrailsDomainRefdataHelpers.getCategoryString (targetClass, pd.name)
        final boolean defaultInternal = GrailsDomainRefdataHelpers.getCategoryDefaultInternal (targetClass, pd.name)
        
        targetClass.metaClass.static."get${upperName}Category" = { typeString }
        log.debug ("Added 'get${upperName}Category' to ${targetClass}")
        
        targetClass.metaClass.static."get${upperName}DefaultInternal" = { defaultInternal }
        log.debug ("Added 'get${upperName}DefaultInternal' to ${targetClass}")

        // Add static method for refdata values lookup.
        targetClass.metaClass.static."all${upperName}Values" << { Map parameters = [:] ->
          // Default read only
          def param = ["readOnly": true]
          if (parameters) param.putAll(parameters)

          // Do the lookup.
          return typeClass.findAll('FROM ' + typeClass.simpleName + ' as rdv WHERE rdv.owner.desc=:desc', [desc: "${typeString}"], param)
        }
        
        // Lookup single value.
        targetClass.metaClass.static."lookup${upperName}" << { String value ->
          // Do the lookup.
          def val = typeClass.find('FROM ' + typeClass.simpleName + ' as rdv WHERE rdv.value=:val AND rdv.owner.desc=:desc', [val: typeClass.normValue(value), desc:"${typeString}"],  ["readOnly": true])
          val
        }
        
        targetClass.metaClass.static."lookupOrCreate${upperName}" << { String value ->
          // Do the lookup.
          typeClass.lookupOrCreate(typeString, value, value, defaultInternal, typeClass)
        }

        log.debug ("Added static methods ['all${upperName}Values', 'lookup${upperName}(value)', 'set${upperName}FromString(value)', 'lookupOrCreate${upperName}(value)'] to ${targetClass}")
        
        // Add instance method method for setting refdata value from string.
        targetClass.metaClass."set${upperName}FromString" = { final String value ->
          
          log.info "${targetClass.simpleName}.set${upperName}FromString ( '${value}' )"
          delegate."set${upperName}" ( targetClass."lookupOrCreate${upperName}" (value) )
        }

        log.debug ("Added instance method 'set${upperName}FromString' to ${targetClass}")
      }

      // Extend the class' collection properties of this type.
      props = GrailsClassUtils.getPropertiesAssignableToType(targetClass, Collection.class)
      
      for (final PropertyDescriptor pd : props) {
        PersistentProperty pp = pe.getPropertyByName ("${pd.name}")
        Class<?> genericClass = pp ? ((pp instanceof Association) ? pp.associatedEntity?.javaClass : pp.type) : null
        if (genericClass && GrailsClassUtils.isAssignableOrConvertibleFrom(RefdataValue.class, genericClass)) {

          // The ClassName representation of the property name.
          String upperName = GrailsNameUtils.getClassName(pd.name)
          final String typeString = GrailsDomainRefdataHelpers.getCategoryString (targetClass, pd.name)
          final boolean defaultInternal = GrailsDomainRefdataHelpers.getCategoryDefaultInternal (targetClass, pd.name)
        
          targetClass.metaClass.static."get${upperName}Category" = { typeString }
          log.debug ("Added '${pd.name}Category' to ${targetClass}")
          
          targetClass.metaClass.static."get${upperName}DefaultInternal" = { defaultInternal }
          log.debug ("Added 'get${upperName}DefaultInternal' to ${targetClass}")

          targetClass.metaClass.static."all${upperName}Values" << { Map parameters = [:] ->
            // Default read only
            def param = ["readOnly": true]
            if (parameters) param.putAll(parameters)

            // Do the lookup.
            return genericClass.findAll('FROM ' + genericClass.simpleName + ' as rdv WHERE rdv.owner.desc=:desc', [desc: "${typeString}"], param)
          }
          
          // Add instance method method for adding refdata value from string.
          targetClass.metaClass."addTo${upperName}FromString" << { String value ->

            // Set the refdata value by using the lookupOrCreate method
            def rdv = genericClass.lookupOrCreate("${typeString}", value, value, defaultInternal, genericClass)

            delegate."addTo${upperName}" ( rdv )
          }

          log.debug ("Added methods ['all${upperName}Values','addTo${upperName}FromString','lookup${upperName}ByValue'] to ${targetClass}")
        }
      }
    }
  }
}