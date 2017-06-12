package com.k_int.web.toolkit.utils

class MapUtils {
  public static Map flattenMap( Map m, String separator = '.', int depth = 0) {
    return flattenTheMap (m, separator, depth)
  }
  
  public static Map merge(Map... maps) {

    if (maps.length == 1) {
      // No need to do anything just return the original.
      return maps[0]
    } 
      
    Map result = [:]
    maps.each { map ->
      map.each { k, v ->
          result[k] = result[k] instanceof Map ? merge(result[k], v) : v
      }
    }
    result
  }
  
  private static Map flattenTheMap( Map m, String separator = '.', int depth = 0, int currentDepth = 0) {
    m?.collectEntries { k, v ->
      if (v instanceof Map && (depth == 0 || (currentDepth < depth))) {
        return flattenTheMap(v, separator, depth, currentDepth+1).collectEntries { q, r ->  [(k + separator + q): r] }
      } else {
        return [(k):v]
      }
    }
  }
}
