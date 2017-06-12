package com.k_int.web.toolkit.databinding

import grails.databinding.SimpleMapDataBindingSource
import grails.util.GrailsNameUtils;


public class Helpers {


  /**
   * this closure is a helper for use when two classes are joined via an intermediate join object, for example, the traditional (a)Order -< (b)OrderLine >- (c)Product model.
   *
   * We assume for this helper that the intermediate object is a first class object, with an ID property which can be used to identify specific relationships.
   *
   * When databinding, we're concerned with the following situations
   *
   *    A new relationship(b) has been created to an existing object(c)
   *    A new relationship(b) has been created to a new object (c)
   *    A relationship has been removed.
   *
   *    We are not processing
   *
   *    A relationship(b) is updated to point to a new object(c) - that is a removal and an addition ideally (Actually, this is a bit of an open q)
   *
   * Basic algorithm
   *
   * In order to process members of the collection, all target entities(c) need to exist, so the first task is to resolve those entities and lookup or create as appropriate
   * Once all resolved, ordering the lists by their PK will allow us to detect rows removed from the JSON which are present in the database
   *
   * @Param obj - the parent object (a)
   * @Param source - the JSON
   */
  public static Object manyToManyCollectionHelper(obj, 
                                                  source, 
                                                  collectionProperty, 
                                                  listItemClass,
                                                  grailsWebDataBinder,
                                                  mappedBy,
                                                  managed) {   // managed if listItemClass belongsTo obj
    try {
      println("manyToManyCollectionHelper(${obj},${source},${collectionProperty})");
      if ( obj ) {
        println("inside ${obj[collectionProperty]} source:${source[collectionProperty]}");
        if ( obj[collectionProperty] == null ) {
          println("initialise");
          obj[collectionProperty] = []
          println("assignment complete");
        }
      }

      // We might need to save the parent object before we try and create any child objects
//      obj.save(flush:true, failOnError:true);

      def listFromJson = buildBoundListFromJson(obj, source, collectionProperty, listItemClass, grailsWebDataBinder, mappedBy, managed);
      println("Comparing ${obj[collectionProperty]} and ${listFromJson}");

      listFromJson.each { o ->
        if ( obj[collectionProperty].contains(o) ) {
          println("Instance ${o} (${o.hashCode()}) already present in collection property");
        }
        else {
          println("Adding instance ${o} - of class ${o.class.name} to ${collectionProperty}");
          obj."addTo${GrailsNameUtils.getClassName(collectionProperty)}" (o);
        }
      }


    }
    catch ( Exception e ) {
      e.printStackTrace()
    }
    finally {
      println("m2mch Returning ${obj[collectionProperty]}");
    }

    return obj[collectionProperty];
  }

  public static List buildBoundListFromJson(owner, 
                                            source, 
                                            collectionProperty, 
                                            listItemClass,
                                            grailsWebDataBinder,
                                            mappedBy,
                                            managed) {


    // In some situations, it is necessary to ensure that the owning class has been saved before we can 
    // create and save dependent objects. This will depend upon the belongsTo relationship. For now, we assume
    // a save is needed

    // Cycle through the members of the list. If the class of the list members supports a static fuzzyMatch method, then use that to
    // try and match the json object to a domain object. Otherwise, see if an id is present in the json, if not, new instance
    def result = []
    println("buildBoundListFromJson ${source[collectionProperty]}");
    source[collectionProperty].each { list_item ->
      def needs_save = false;

      def list_item_domain_object = listItemClass.fuzzyMatch(owner,list_item);

      if ( list_item_domain_object == null ) {
        println("Create new instance... of cls:${listItemClass.name} mappedBy:${mappedBy}\n\n");
        list_item_domain_object = listItemClass.newInstance();
        needs_save = true
      }

      // Now do a data binding using the source li against that object
      try {
        println("Call Bind json to new list item ${list_item_domain_object} ${list_item_domain_object.class.name} ${list_item}");
        grailsWebDataBinder.bind(list_item_domain_object, new  SimpleMapDataBindingSource(list_item));

        if ( mappedBy ) {
          println("\n\nAssigning ${mappedBy} in new ${listItemClass.name} value of ${owner}");
          list_item_domain_object[mappedBy] = owner;
        }
        else {
          println("\n\nNo mapped by");
        }
        // println("completed Bind json to new list item ${list_item_domain_object} ${list_item_domain_object.errors}");
        // list_item_domain_object.errors?.each { err ->
        //   println('\n\n ERROR BINDING DATA:'+err);
        // }
      }
      catch ( Exception e ) {
        e.printStackTrace();
      }
      println("Completed Bind... ${list_item_domain_object}");

      if (needs_save && list_item_domain_object && !managed ) {
        println("SAVE new list object ${list_item_domain_object}");
        list_item_domain_object.save(flush:true, failOnError:true);
      }

      println("Adding ${list_item_domain_object.hashCode()} to result list");
      result.add(list_item_domain_object)
    }
    result;
  }
}
