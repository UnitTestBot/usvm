package org.usvm.instrumentation.util

import java.lang.Exception

@Suppress("UNCHECKED_CAST")
class Try<T> internal constructor(val unsafe: Any?) {
    @PublishedApi internal val failure: Failure? get() = unsafe as? Failure

    @PublishedApi internal data class Failure(val exception: Throwable)

    companion object {
        fun <T> just(value: T) = Try<T>(value)
        fun <T> exception(exception: Throwable) = Try<T>(
            Failure(exception)
        )
    }

    val isFailure: Boolean get() = unsafe is Failure
    val isSuccess: Boolean get() = !isFailure
    val exception: Throwable get() = asserted(isFailure) { failure!!.exception }

    fun getOrDefault(value: T) = when {
        isSuccess -> unsafe as T
        else -> value
    }

    inline fun getOrElse(block: () -> T) = when {
        isSuccess -> unsafe as T
        else -> block()
    }

    fun getOrNull() = when {
        isSuccess -> unsafe as T
        else -> null
    }

    fun getOrThrow(): T = getOrThrow { this }

    inline fun getOrThrow(action: Throwable.() -> Throwable): T {
        failure?.apply {
            val throwable = exception.action()
            throw throwable
        }
        return unsafe as T
    }

    inline fun <K> map(action: (T) -> K) = when {
        isSuccess -> just(action(unsafe as T))
        else -> exception(failure!!.exception)
    }

    inline fun let(action: (T) -> Unit) = when {
        isSuccess -> action(unsafe as T)
        else -> {}
    }
}

inline fun <T> tryOrNull(action: () -> T): T? = `try`(action).getOrNull()

inline fun <T> safeTry(body: () -> T) = `try`(body)

inline fun <T> `try`(body: () -> T): Try<T> = try {
    Try.just(body())
} catch (e: Throwable) {
    Try.exception(e)
}

inline fun <T> asserted(condition: Boolean, action: () -> T): T {
    assert(condition)
    return action()
}


@Suppress("ControlFlowWithEmptyBody")
fun assert(cond: Boolean) = if (!cond) throw AssertionException() else {}

class AssertionException(message: String) : KtException(message) {
    constructor() : this("")
}

@Suppress("unused")
open class KtException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, inner: Throwable) : super(message, inner)
}