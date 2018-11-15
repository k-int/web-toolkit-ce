package com.k_int.web.toolkit.tags;

import com.k_int.web.toolkit.custprops.types.CustomPropertyContainer

trait Taggable {
  static hasMany = [
    tags: Tag
  ]
}
