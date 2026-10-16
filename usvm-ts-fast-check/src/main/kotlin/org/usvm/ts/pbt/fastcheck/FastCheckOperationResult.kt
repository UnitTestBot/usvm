package org.usvm.ts.pbt.fastcheck

/** Expected budget exhaustion travels as data; backend and protocol failures retain their exception contracts. */
internal sealed interface FastCheckOperationResult<out T> {
    data class Completed<T>(val value: T) : FastCheckOperationResult<T>

    data class TimedOut(val message: String) : FastCheckOperationResult<Nothing>
}
