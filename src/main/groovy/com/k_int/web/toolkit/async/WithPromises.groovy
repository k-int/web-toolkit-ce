package com.k_int.web.toolkit.async

import java.util.concurrent.TimeUnit

import grails.async.Promises

class WithPromises extends Promises {
  
  static {
    Promises.@promiseFactory = new QueueingThreadPoolPromiseFactory( 25, 2000, 1L, TimeUnit.MINUTES )
  }
  
}
