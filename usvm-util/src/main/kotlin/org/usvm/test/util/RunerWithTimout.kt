package org.usvm.test.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread
import kotlin.time.Duration

inline fun <T> runWithTimout(timeout: Duration, crossinline body: () -> T): T {
    val resultFuture = CompletableFuture<T>()
    val runner = thread(name = "TestRunner", start = false) {
        try {
            resultFuture.complete(body())
        } catch (ex: Throwable) {
            resultFuture.completeExceptionally(ex)
        }
    }
    try {
        runner.start()
        return resultFuture.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    } catch (ex: TimeoutException) {
        while (runner.isAlive) {
            @Suppress("DEPRECATION")
            runner.stop()
            Thread.yield()
        }
        throw ex
    } catch (ex: ExecutionException) {
        throw ex.cause!!
    }
}
