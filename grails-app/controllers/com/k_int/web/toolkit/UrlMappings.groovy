package com.k_int.web.toolkit

class UrlMappings {

  static mappings = {
    "/config/$extended?" (controller: 'config' , action: "resources")
    "/config/schema/$type" (controller: 'config' , action: "schema")
    "/config/schema/embedded/$type" (controller: 'config' , action: "schemaEmbedded")
    
    "/ref/blank/$domain/$prop" (controller: 'ref', action: 'blank')
  }
}
