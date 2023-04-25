package com.k_int.web.toolkit.async

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RunnableFuture
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.atomic.AtomicInteger

import javax.annotation.PreDestroy

import org.grails.async.factory.BoundPromise
import org.grails.async.factory.future.ExecutorPromiseFactory
import org.grails.async.factory.future.FutureTaskPromise

import grails.async.Promise
import grails.async.PromiseList
import grails.async.factory.AbstractPromiseFactory
import groovy.transform.CompileStatic

@CompileStatic
class QueueingThreadPoolPromiseFactory extends AbstractPromiseFactory implements Closeable, ExecutorPromiseFactory {

  final @Delegate ExecutorService executorService

  public QueueingThreadPoolPromiseFactory(int maxPoolSize = 1000, int maxQueueLength = 1000, long timeout = 3L, TimeUnit unit = TimeUnit.MINUTES) {
    final QueueingThreadPoolPromiseFactory pf = this
    this.executorService = new ThreadPoolExecutor(5, maxPoolSize, timeout, unit,
          new LinkedBlockingQueue<Runnable>(maxQueueLength),
          new NamedThreadFactory('Promises'),
          new CallerRunsPolicy()) {
          
      @Override
      protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new FutureTaskPromise<T>(pf,callable)
      }

      @Override
      protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new FutureTaskPromise<T>(pf,runnable, value)
      }
    }
  }

  @Override
  def <T> Promise<T> createPromise(Class<T> returnType) {
    return new BoundPromise<T>(null)
  }

  @Override
  Promise<Object> createPromise() {
    return new BoundPromise<Object>(null)
  }

  @SuppressWarnings("unchecked")
  @Override
  def <T> Promise<T> createPromise(Closure<T>... closures) {
    Promise<T> tasks
    
    if(closures.length == 1) {
      def callable = closures[0]
      applyDecorators(callable, null)
      tasks = (Promise<T>)executorService.submit((Callable)callable)
    }
    else {
      PromiseList list = new PromiseList()
      for(c in closures) {
        list.add(c as Closure)
      }
      tasks = list
    }
    
    tasks
  }

  @Override
  def <T> List<T> waitAll(List<Promise<T>> promises) {
    promises.collect() { Promise<T> p -> p.get() }
  }

  @Override
  def <T> List<T> waitAll(List<Promise<T>> promises, long timeout, TimeUnit units) {
    promises.collect() { Promise<T> p -> p.get(timeout, units) }
  }

  @Override
  def <T> Promise<List<T>> onComplete(List<Promise<T>> promises, Closure<?> callable) {
    (Promise<List<T>>)executorService.submit( (Callable) {
      while(promises.every() { Promise p -> !p.isDone() }) {
        // wait
      }
      def values = promises.collect() { Promise<T> p -> p.get() }
      callable.call(values)
    })
  }

  @Override
  def <T> Promise<List<T>> onError(List<Promise<T>> promises, Closure<?> callable) {
    (Promise<List<T>>)executorService.submit((Callable) {
      while(promises.every() { Promise p -> !p.isDone() }) {
        // wait
      }
      try {
        promises.each() { Promise<T> p -> p.get()  }
      } catch (Throwable e) {
        callable.call(e)
        return e
      }
    })
  }

  @Override
  @PreDestroy
  void close() {
    if(!executorService.isShutdown()) {
      executorService.shutdown()
    }
  }
  
  private static class NamedThreadFactory implements ThreadFactory {
    private static final ConcurrentHashMap<String, AtomicInteger> poolNumber = new ConcurrentHashMap<String, AtomicInteger>();
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    public NamedThreadFactory(final String name) {
      
      // Default.
      if (!poolNumber.containsKey(name)) {
        poolNumber[name] = new AtomicInteger(1)
      }
      group = System.getSecurityManager()?.getThreadGroup() ?: Thread.currentThread().getThreadGroup()
      namePrefix = "${name}#${poolNumber[name].getAndIncrement()}T#"
    }

    public Thread newThread(Runnable r) {
      
      String threadName = namePrefix + threadNumber.getAndIncrement()
      System.out.println "Creating thread named ${threadName}"
      Thread t = new Thread(group, r,
        namePrefix + threadNumber.getAndIncrement(),
        0
      )
      if (t.isDaemon())
          t.setDaemon(false)
      if (t.getPriority() != Thread.NORM_PRIORITY)
          t.setPriority(Thread.NORM_PRIORITY)
      return t
    }
  }
}

