package com.k_int.web.toolkit.tags;

import com.k_int.web.toolkit.custprops.types.CustomPropertyContainer

/**
 * A marker interface that defines an entity as being Taggable - creating a collection
 * of tag entities that can be attached to the target.
 */
trait Taggable {
  static hasMany = [
    tags: Tag
  ]
}
