package com.k_int.web.toolkit.tags;

import com.k_int.web.toolkit.custprops.types.CustomPropertyContainer

import groovy.transform.CompileStatic

/**
 * A marker interface that defines an entity as being Taggable - creating a collection
 * of tag entities that can be attached to the target.
 */
@CompileStatic
trait Taggable {
  Set<Tag> tags
  
  static hasMany = [
    tags: Tag
  ]
}
