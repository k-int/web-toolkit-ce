package com.k_int.web.toolkit.json

import org.apache.commons.lang.ClassUtils
import org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.web.plugins.support.ValidationSupport
import org.springframework.context.ApplicationContext
import com.k_int.web.toolkit.utils.DomainUtils
import grails.gorm.validation.Constrained
import grails.gorm.validation.ConstrainedProperty
import grails.util.GrailsNameUtils
import grails.util.Holders
import groovy.transform.CompileStatic

class JsonSchemaUtils {
  
  private static String resolveSimpleName (final def obj) {
    def type = obj instanceof PersistentEntity ? obj.javaClass : obj
    type.getSimpleName()
  }
  
  public static Map<String, ?> jsonSchema ( final def obj, String linkPrefix = '', boolean embedReferences = false) {
    
    // Add the schema line.
    def schema = ['$schema': 'http://json-schema.org/schema#'] + buildJsonSchema(obj, linkPrefix, embedReferences)
    schema  
  }
  
  private static Map<String, ?> buildJsonSchema ( final def obj, String linkPrefix, boolean embedReferences, Map<String, Map> existingSchemas = [:]) {
    
    // Sort out the ID link prefix.
    if (linkPrefix) {
      linkPrefix = linkPrefix.endsWith('/') ? linkPrefix : "${linkPrefix}/"
    } else {
      // Ensure blank string.
      linkPrefix = ''
    }
    
//    final Set<String> rootProps = ['$id', 'title']
    
    // Lets resolve to a Grails domain if possible.
    def target = DomainUtils.resolveDomainClass( obj )
    
    // Lets add some other useful properties.
    String schemaName = resolveSimpleName(target ?: obj)
    
    // The map to hold our json representation. Initialise with ID.
    Map<String, ?> json = ['$id' : "${linkPrefix}${schemaName}" as String]
    
    // The title.
    json['title'] = GrailsNameUtils.getNaturalName(schemaName)
    
    // Get the type of this object first.
    json += jsonType(target ?: obj)
    String type = json['type']
    
    // Only objects need expanding.
    if (type == 'object') {
      
      // Check to see if we have any children.
      def children = obj.properties
      
      if (children) {
    
        // Flag for removal.
//        Set<String> propsMoved = []
        
        // We should now add this schema to the map of seen schemas. Along with the location. This is the root schema so the relative
        // location would simply be #/. However because of various parsing failures, the reference #/ doesn't seem to work, so
        // we leave the root of the schema as is and copy to the definitions. 
//        existingSchemas[schemaName] = [
//          ref : ['$ref' : "#/definitions/${schemaName}"],
//          schema: json.collectEntries { entry ->
//            if (rootProps.contains(entry.key)) {
//              return [:]
//            }
//            
////            propsMoved << entry.key
//            
//            // Return the entry here.
//            [(entry.key) : entry.value]
//          }
//        ]
        
        
        // remove form the json root.
//        json.keySet().removeAll(propsMoved)
        
        // Add the ref.
//        json.putAll(existingSchemas[schemaName].ref)
      
        // Lets take a look at the properties now.
        json.putAll(buildChildren (target ?: obj, embedReferences ? '#/definitions/' : "${linkPrefix.length() < 1 ? '/' : linkPrefix}" , existingSchemas))
        
        // References now need to be filtered.
        if (embedReferences) {
          json['definitions'] = existingSchemas.collectEntries { entry -> 
            entry.value?.schema != null ? [(entry.key) : entry.value.schema] : [:]
          }
        } else {
          // The embedded references are no longer needed.
          json.remove('definitions')
//          json['definitions']?.keySet()?.each {
//            if (schemaName != it) {
//              // Remove.
//              json['definitions'].remove(it)
//            }
//          }
        }
      }
    }
    
    json
  }
  
  private static Map<String, ?> buildChildren (final def obj, String idPrefix, Map<String, Map> existingSchemas) {
    
    final Map<String, ?> json = [:]
    final Map<String, ?> properties = [:]
    
    Map<String, ?> conMap = [:]
    if (obj instanceof PersistentEntity) {
      
      // Grab the contraints evaluator.
      Map<String, ConstrainedProperty> constraints = ValidationSupport.getConstrainedPropertiesForClass(obj.javaClass)
      
      // This schema needs building and then the reference adding.
      obj.persistentProperties.each { PersistentProperty prop ->
        
        // Gorm adds some properties of type Object to track identifiers.
        if (!(prop.type == Object && prop.name.endsWith ('Id'))) {
          
          // Although referencedPropertyType is supposed to delegate to type if not a reference,
          // this doesn't work in every instance. For instance when declaring a custom primitive Identifier
          // type. Like String based UIDs.
          def propType = (prop instanceof Association) ? prop.associatedEntity : prop.type
          
          // Derive the schema name for the target object type.
          String schemaName = resolveSimpleName(propType)
          def val = existingSchemas[schemaName]?.ref
          
          // If we haven't already have seen this type build.
          if (!val) {
            // Let's generate
            
            // Add the title.
            val = [:]//['title' : GrailsNameUtils.getNaturalName(schemaName)]
            
            // Get the type of this object.
            val += jsonType(propType)
            String type = val['type']
            
            // Only objects need expanding.
            if (type == 'object') {
              
              // Check to see if we have any children.
              def children = propType.properties
              
              if (children) {
                def schema = [
                  'ref' : [ '$ref' : "${idPrefix}${schemaName}" as String ]
                ]
                
                // Check for the object identity.
                PersistentProperty theId = obj.identity
                String idType = jsonType(theId.type)?.type
                
                if (idType) {
                  
                  schema.ref = [
                    'anyOf' : [
                      schema.ref,
                      [
                        type: 'object',
                        properties: [
                          (theId.name) : [
                            type: idType
                          ]
                        ]
                      ]
                    ]
                  ]
                }
                
                // We should now add this schema to the map of seen schemas. Along with the location. 
                existingSchemas[schemaName] = schema
              
                // Lets take a look at the properties now.
                val.putAll ( buildChildren (propType, idPrefix, existingSchemas))
                
                // Add the schema once built too!
                existingSchemas[schemaName]['schema'] = val
                
                // We should change val to be a reference now.
                val = existingSchemas[schemaName]['ref']
              }
            }
            
            
            // Examine any constraints.
            if (constraints.containsKey(prop.name)) {
              val = examineContraints(constraints[prop.name], type, [prop: val, owner: json ]).get('prop')
            }
          }
          
          // Get the actual type in case of collection. 
          def theType = jsonType(prop.type)
          if (theType.type == 'array') {
            // This property is a collection
            val = [
              type: 'array',
              items: val 
            ]
            
          }
          
          // Add to the JSON output.
          properties[prop.name] = val
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
          val = jsonType(prop.type)
          String type = val['type']
          
          // Special collections type.
          if (type == 'array') {
            
            // This property is a collection
            val['items'] = ['type' : 'object']
          }
        }
        
        properties[prop.name] = val
      }
    }
    
    if (properties) {
      json ['properties'] = properties
    }
    
    json
  }
  
  @CompileStatic
  private static Map<String, ?> examineContraints (final ConstrainedProperty con, String jsonType, final Map<String, ?> props) {
    
    // Constraints.
    // Props map should contain a prop key and an owner key.
    def p = props['prop']
    def o = props['owner']
    
    if (!con.nullable) {
      // Required exists on the object that declares the property and not on the property definition itself.
      Set<String> required = (Set<String>)o['required']
      if (!required) {
        required = [] as Set<String>
        // initialise as set.
        o['required'] = required
      }
      
      // Add the property.
      required << con.propertyName
//    } else {
//      // Can be either the declared type or null.
//      Map<String,?> anyOf = [anyOf : [p, [ "type": "null" ]]]
//      
//      // Replace in the original map, but do not change the reference above.
//      props['prop'] = anyOf
    }
    
    // Type specifics.
    if (con && jsonType && props) {
      switch (jsonType) {
        case 'number' :
          // Numbers can have max, min and a scale constraint in JSON.
          if (con.scale != null) {
            p['multipleOf'] = Math.pow(10d, (con.scale * -1) as double)
          }
        case 'integer' :
          // Max and min with no scale as integers are whole.
          def etremity = con.max
          if (etremity != null) {
            p['maximum'] = etremity
          }
          
          etremity = con.min
          if (etremity != null) {
            p['minimum'] = etremity
          }
          break
        case 'string' :
          def etremity = con.maxSize
          if (etremity != null) {
            p['maxLength'] = etremity
          }
          
          etremity = con.minSize
          if (etremity != null) {
            p['minLength'] = etremity
          } else {
            // Check if blank is allowed. we can then infer the minimum.
            String format = p['format']
            if (con.blank && !format?.startsWith('date')) {
              p['minLength'] = 0
//            } else {
//              // None-Blank string has to be greater than 1 character. 
//              p['minLength'] = 1
            }
          }
          break
          
        case 'array' :
          def etremity = con.maxSize
          if (etremity != null) {
            p['maxItems'] = etremity
          }
          
          etremity = con.minSize
          if (etremity != null) {
            p['minItems'] = etremity
          }
          break
      }
    }
    
    // Return the map. It has possibly been mutated and the return value may differ from the original.
    props
  }
  
  @CompileStatic
  private static Map<String,?> jsonType ( final def propertyType ) {
    Map<String,?> theDef = [:]
    if (propertyType == null) {
      theDef.type = 'null'
    } else {
      
      // We need the class.
      Class theClass = (propertyType instanceof Class) ? propertyType : propertyType.class
      
      // Run the class through the spring ClassUtils to get wrapper types for primitives.
      if (theClass.isPrimitive()) {
        theClass = ClassUtils.primitiveToWrapper(theClass)
      }
      
      switch (theClass) {
        
        case {Number.isAssignableFrom(theClass)} :
          theDef.type = jsonNumberSpec (theClass as Class<Number>)
          break
        
        case {Date.isAssignableFrom(theClass)} :
          theDef.format = 'date-time'

        case {String.isAssignableFrom(theClass)} :
          theDef.type =  'string'
          break
        
        case {Boolean.isAssignableFrom(theClass)} :
          theDef.type =  'boolean'
          break
        
        case {Collection.isAssignableFrom(theClass) && !Map.isAssignableFrom(theClass)} :
          // Maps, even though a collection must be represented as an object.
          theDef.type =  'array'
          break
        
        default :
          // The default is 'object'
          theDef.type =  'object'          
          break
      }
    }
    
    theDef
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
