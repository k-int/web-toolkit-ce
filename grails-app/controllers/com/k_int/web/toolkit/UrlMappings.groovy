package com.k_int.web.toolkit

class UrlMappings {

  static mappings = {
    "/config" (controller: 'config' , action: "resources")
    "/config/schema/$type" (controller: 'config' , action: "schema")
  }
}
