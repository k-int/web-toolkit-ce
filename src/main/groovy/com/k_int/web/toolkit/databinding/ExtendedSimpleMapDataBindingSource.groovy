package com.k_int.web.toolkit.databinding

import grails.databinding.SimpleMapDataBindingSource
import groovy.transform.CompileStatic
import groovy.util.logging.Log4j

@Log4j
@CompileStatic
class ExtendedSimpleMapDataBindingSource extends SimpleMapDataBindingSource {
  
  ExtendedSimpleMapDataBindingSource (SimpleMapDataBindingSource source) {
    // Call our overridden map constructor. 
    this ( source.map )
    
    log.debug ("Created a new extended source from a SimpleMap source.")
  }
  
  ExtendedSimpleMapDataBindingSource(Map map) {
    
    // Super constructor...
    super ( map )
    
    // We can now remove the id if it matches a new item.
    if (ExtendedWebDataBinder.identifierValueDenotesNewObject(map['id'])) {
      // Remove the map entry.
      map.remove('id')
      log.debug ("Found id value denoting new object. Removing id from source now.")
    }
  }
}