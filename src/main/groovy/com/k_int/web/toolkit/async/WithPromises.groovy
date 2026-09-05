package com.k_int.web.toolkit.async

import java.util.concurrent.TimeUnit

import grails.async.Promises
import org.springframework.core.task.TaskDecorator

class WithPromises extends Promises {
  
  static {
    Promises.@promiseFactory = new QueueingThreadPoolPromiseFactory( 25, 2000, 1L, TimeUnit.MINUTES )
  }

  static void configureTaskDecorator(TaskDecorator decorator) {
    ((QueueingThreadPoolPromiseFactory)Promises.@promiseFactory).taskDecorator = decorator
  }
  
}
