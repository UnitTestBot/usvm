package org.usvm.util

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue

class TimeoutException : RuntimeException() {
    // Make it fast
    override fun fillInStackTrace(): Throwable = this
}

@OptIn(ExperimentalTime::class)
class TimeLimitedIterator<T>(private val iterator: Iterator<T>, private val timeout: Duration) : Iterator<T> {
    private var elapsedTime = Duration.ZERO

    override fun hasNext(): Boolean = doWithTimeout { iterator.hasNext() }

    override fun next(): T = doWithTimeout { iterator.next() }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun throwIfExpiredTimeout() {
        if (elapsedTime >= timeout) {
            throw TimeoutException()
        }
    }

    private inline fun <T> doWithTimeout(block: () -> T): T {
        throwIfExpiredTimeout()

        val (value, time) = measureTimedValue { block() }
        elapsedTime += time

        return value
    }
}

fun <T> Sequence<T>.timeLimitedIterator(timeout: Duration): TimeLimitedIterator<T> =
    TimeLimitedIterator(iterator(), timeout)

fun <T> Iterable<T>.timeLimitedIterator(timeout: Duration): TimeLimitedIterator<T> =
    TimeLimitedIterator(iterator(), timeout)
