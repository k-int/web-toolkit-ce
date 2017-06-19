package com.k_int.web.toolkit.json

import com.k_int.web.toolkit.utils.DomainUtils

import grails.core.GrailsDomainClass
import grails.core.GrailsDomainClassProperty
import grails.util.GrailsNameUtils
import grails.validation.ConstrainedProperty
import grails.validation.Constraint
import groovy.transform.CompileStatic

import java.util.Collection

import org.grails.core.DefaultGrailsDomainClassProperty
import org.grails.validation.MaxConstraint
import org.grails.validation.RangeConstraint


class JsonSchemaUtils {
  
  private static String resolveSimpleName (final def obj) {
    def type = obj instanceof GrailsDomainClass ? obj.clazz : obj
    type.simpleName
  }
  
  public static Map<String, ?> jsonSchema ( final def obj, String linkPrefix = '') {
    
    // Add the schema line.
    def schema = ['$schema': 'http://json-schema.org/schema#'] + buildJsonSchema(obj, linkPrefix)
    schema  
  }
  
  private static Map<String, ?> buildJsonSchema ( final def obj, String idPrefix = '', Map<String, Map> existingSchemas = [:]) {
    
    // Sort out the ID link prefix.
    if (idPrefix) {
      idPrefix = idPrefix.endsWith('/') ? idPrefix : "${idPrefix}/"
    } else {
      // Ensure blank string.
      idPrefix = ''
    }
    
    // Lets resolve to a Grails domain if possible.
    def target = DomainUtils.resolveDomainClass( obj )
    
    // Lets add some other useful properties.
    String schemaName = resolveSimpleName(target ?: obj)
    
    // The map to hold our json representation. Initialise with ID.
    Map<String, ?> json = ['id' : "${idPrefix}${schemaName}" as String]
    
    // The title.
    json['title'] = GrailsNameUtils.getNaturalName(schemaName)
    
    // Get the type of this object first.
    String type = jsonType(target ?: obj)
    json['type'] = type
    
    // Only objects need expanding.
    if (type == 'object') {
      
      // Check to see if we have any children.
      def children = obj.properties
      
      if (children) {
    
        // We should now add this schema to the map of seen schemas. Along with the location. This is the root schema so the relative
        // location would simply be #/
        existingSchemas[schemaName] = [
          ref : ['$ref' : '#/']
        ]
        
        // Add it to this root schema.
        json['references'] = existingSchemas
      
        // Lets take a look at the properties now.
        json.putAll(buildChildren (target ?: obj, '#/references/', existingSchemas))
        
        // References now need to be filtered.
        json['references'] = existingSchemas.collectEntries { entry -> 
          entry.value?.schema != null ? [(entry.key) : entry.value.schema] : [:]
        }
      }
    }
    
    json
  }
  
  private static Map<String, ?> buildChildren (final def obj, String idPrefix, Map<String, Map> existingSchemas) {
    
    final Map<String, ?> json = [
      properties : [:]
    ]
    
    Map<String, ?> conMap = [:]
    
    if (obj instanceof GrailsDomainClass) {
      
      // Grab the contrained property map.
      Map <String,ConstrainedProperty> constraints =  obj.constrainedProperties
      
      // This schema needs building and then the reference adding.
      obj.properties.each { GrailsDomainClassProperty prop ->
        
        // Gorm adds some properties of type Object to track identifiers.
        if (!(prop.type == Object && prop.name.endsWith ('Id'))) {
          
          // Derive the schema name for the target object type.
          String schemaName = resolveSimpleName(prop.referencedPropertyType)
          def val = existingSchemas[schemaName]?.ref
          
          // If we haven't already have seen this type build.
          if (!val) {
            // Let's generate
            
            // Add the title.
            val = ['title' : GrailsNameUtils.getNaturalName(schemaName)]
            
            // Get the type of this object.
            String type = jsonType(prop.referencedPropertyType)
            val['type'] = type
                
            // Examine any constraints.
            ConstrainedProperty con = constraints[prop.name]
            if (con) {
              Map<String, ?> cMap = examineContraints(con, type, [prop: [:], owner: conMap ])
            }
            
            // Only objects need expanding.
            if (type == 'object') {
              
              // Check to see if we have any children.
              def children = prop.referencedPropertyType.properties
              
              if (children) {
            
                // We should now add this schema to the map of seen schemas. Along with the location. This is the root schema so the relative
                // location would simply be #/
                existingSchemas[schemaName] = [
                  'ref' : [ '$ref' : "${idPrefix}${schemaName}" as String ]
                ]
              
                // Lets take a look at the properties now.
                val.putAll ( buildChildren (prop.referencedDomainClass ?: prop.referencedPropertyType, idPrefix, existingSchemas))
                
                // Add the schema once built too!
                existingSchemas[schemaName]['schema'] = val
                
                // We should change val to be a reference now.
                val = existingSchemas[schemaName]['ref']
              }
            }
          }
          
          // Get the actual type in case of collection. 
          String theType = jsonType(prop.type)
          if (theType == 'array') {
            // This property is a collection
            val = [
              type: 'array',
              items: val 
            ]
            
          }
          
          // Add to the JSON output.
          json['properties'][prop.name] = val
        }
      }
    } else {
      
      obj.metaClass.properties.each { MetaProperty prop ->
        
        // Derive the schema name for the type.
        String schemaName = resolveSimpleName(prop.type)
        def val = existingSchemas[schemaName]?.ref
        
        // If we haven't already have seen this type build.
        if (!val) {
          // Let's generate. We won't traverse children though as that would make the schema very large, and we don't really care about
          // None-domain complex structures.
          // Get the type of this object first.
          String type = jsonType(prop.type)
          val = ['type': type ]
          
          // Special collections type.
          if (type == 'array') {
            
            // This property is a collection
            val['items'] = ['type' : 'object']
          }
        }
        
        json['properties'][prop.name] = val
      }
    }
    
    json
  }
  
  private static Map<String, ?> examineContraints (final ConstrainedProperty con, final String jsonType, final Map<String, ?> props) {
    // Constraints.
    // Props map should contain a prop key and an owner key.
    Collection<Constraint> allConstraints = con.appliedConstraints
    
    if (con && jsonType && props) {
      switch (jsonType) {
        case 'number' :
          // Numbers can have max, min and a scale constraint in JSON.
          if (con.scale != null) {
            props['prop']['multipleOf'] = Math.pow(10d, (-con.scale) as double)
          }
        case 'integer' :
          // Max and min with no scale as integers are whole.
          def etremity = con.max
          if (etremity != null) {
            props['prop']['maximum'] = etremity
          }
          
          etremity = con.min
          if (etremity != null) {
            props['prop']['minimum'] = etremity
          }
          break
        case 'string' :
          break
      }
    }
  }
  
  @CompileStatic
  private static String jsonType ( final def propertyType ) {
    String type 
    if (propertyType == null) {
      type = 'null'
    } else {
      
      // We need the class.
      Class theClass = (propertyType instanceof Class) ? propertyType : propertyType.class 
      
      switch (theClass) {
        
        case {Number.isAssignableFrom(theClass)} :
          type = jsonNumberSpec (theClass)
          break
        
        case {String.isAssignableFrom(theClass)} :
          type =  'string'
          break
        
        case {Boolean.isAssignableFrom(theClass)} :
          type =  'boolean'
          break
        
        case {Collection.isAssignableFrom(theClass) && !Map.isAssignableFrom(theClass)} :
          // Maps, even though a collection must be represented as an object.
          type =  'array'
          break
        
        default :
          // The default is 'object'
          type =  'object'          
          break
      }
    }
    
    type
  }
  
  @CompileStatic
  private static String jsonNumberSpec ( final Class<Number> number) {
    String type
    
    // Number. Lets determine the sub-type.
    String name = number.simpleName
    
    switch (name) {
      case { String it -> it.endsWith ('Integer') } :
      case { String it -> it.endsWith ('Long') } :
      case { String it -> it.endsWith ('Short') } :
      case { String it -> it.endsWith ('Byte') } :
        type = 'integer'
        break
        
      default :
        // Safest to assume floating point.
        type = 'number'
    }
    
    type
  }
  
}
