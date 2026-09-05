package com.k_int.web.toolkit.async

import grails.async.decorator.PromiseDecorator
import grails.async.decorator.PromiseDecoratorLookupStrategy
import org.springframework.core.task.TaskDecorator
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.Callable
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Timeout(15)
class QueueingThreadPoolPromiseFactorySpec extends Specification {
    def factory = new QueueingThreadPoolPromiseFactory(5, 10)
    def context = new ThreadLocal<String>()
    def reservations = new AtomicInteger()

    def cleanup() {
        context.remove()
        factory.close()
        assert factory.awaitTermination(10, TimeUnit.SECONDS)
    }

    def 'promise decorators wrap execution exactly once for both creation overloads'() {
        given:
        def calls = new AtomicInteger()
        PromiseDecorator decorator = { Closure work ->
            { -> calls.incrementAndGet(); work.call() + '-decorated' }
        } as PromiseDecorator
        factory.addPromiseDecoratorLookupStrategy({ -> [decorator] } as PromiseDecoratorLookupStrategy)

        expect:
        factory.createPromise({ -> 'direct' } as Closure[]).get(10, TimeUnit.SECONDS) == 'direct-decorated'
        factory.createPromise({ -> 'explicit' }, []).get(10, TimeUnit.SECONDS) == 'explicit-decorated'
        calls.get() == 2
    }

    def 'submission context reaches direct execute submit and completion and error callbacks without leaking'() {
        given:
        installDecorator()
        context.set('original-generation')

        when:
        def ordinary = factory.createPromise({ -> context.get() } as Closure[])
        def submitted = factory.submit({ -> context.get() } as Callable)
        def complete = factory.onComplete([ordinary], { values -> context.get() + ':' + values[0] })
        def failure = factory.createPromise({ -> throw new IOException('expected') } as Closure[])
        def seenError = new java.util.concurrent.CompletableFuture<String>()
        def error = factory.onError([failure], { problem -> seenError.complete(context.get()) })
        context.remove()

        then:
        ordinary.get(10, TimeUnit.SECONDS) == 'original-generation'
        submitted.get(10, TimeUnit.SECONDS) == 'original-generation'
        complete.get(10, TimeUnit.SECONDS) == 'original-generation:original-generation'
        seenError.get(10, TimeUnit.SECONDS) == 'original-generation'
        error.get(10, TimeUnit.SECONDS) instanceof Throwable
        factory.submit({ -> context.get() } as Callable).get(10, TimeUnit.SECONDS) == null

        when:
        factory.close()
        assert factory.awaitTermination(10, TimeUnit.SECONDS)

        then:
        reservations.get() == 0
    }

    def 'submission after shutdown rejects and releases captured context'() {
        given:
        installDecorator()
        context.set('original-generation')
        factory.close()

        when:
        factory.submit({ -> assert false: 'Rejected callback ran' } as Callable)

        then:
        thrown(RejectedExecutionException)
        reservations.get() == 0
    }

    void installDecorator() {
        factory.taskDecorator = { Runnable task ->
            String value = context.get()
            reservations.incrementAndGet()
            new ContextTask(task, context, value, reservations)
        } as TaskDecorator
    }

    private static class ContextTask implements Runnable, AutoCloseable {
        final Runnable task
        final ThreadLocal<String> context
        final String value
        final AtomicInteger reservations
        final java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean()
        ContextTask(Runnable task, ThreadLocal<String> context, String value, AtomicInteger reservations) {
            this.task = task; this.context = context; this.value = value; this.reservations = reservations
        }
        void run() {
            if (!claimed.compareAndSet(false, true)) return
            String previous = context.get()
            try { if (value == null) context.remove() else context.set(value); task.run() }
            finally {
                if (previous == null) context.remove() else context.set(previous)
                reservations.decrementAndGet()
            }
        }
        void close() { if (claimed.compareAndSet(false, true)) reservations.decrementAndGet() }
    }
}
