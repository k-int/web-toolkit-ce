package com.k_int.web.toolkit.search

import groovy.transform.AnnotationCollector

@Searchable(value=false,filter=false,sort=false)
@AnnotationCollector
@interface SystemOnly {}