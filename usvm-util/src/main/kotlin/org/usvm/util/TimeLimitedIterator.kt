package org.usvm.util

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

class TimeoutException : RuntimeException() {
    // Make it fast
    override fun fillInStackTrace(): Throwable = this
}

@OptIn(ExperimentalTime::class)
class TimeLimitedIterator<T>(val iterator: Iterator<T>, timeout: Duration) : Iterator<T> {
    private val timeoutMark: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow() + timeout

    override fun hasNext(): Boolean = iterator.hasNext()

    override fun next(): T {
        if (timeoutMark.hasPassedNow()) {
            throw TimeoutException()
        }

        return iterator.next()
    }
}

fun <T> Sequence<T>.timeLimitedIterator(timeout: Duration): TimeLimitedIterator<T> =
    TimeLimitedIterator(iterator(), timeout)

fun <T> Iterable<T>.timeLimitedIterator(timeout: Duration): TimeLimitedIterator<T> =
    TimeLimitedIterator(iterator(), timeout)
