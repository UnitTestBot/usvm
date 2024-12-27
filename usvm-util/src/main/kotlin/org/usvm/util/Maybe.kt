package org.usvm.util

@JvmInline
value class Maybe<out T> private constructor(
    private val rawValue: Any?,
) {
    val isSome: Boolean get() = rawValue !== NONE_VALUE
    val isNone: Boolean get() = rawValue === NONE_VALUE

    fun getOrThrow(): T {
        check(isSome) { "Maybe is None" }
        @Suppress("UNCHECKED_CAST")
        return rawValue as T
    }

    override fun toString(): String {
        return if (isSome) "Some($rawValue)" else "None"
    }

    companion object {
        private val NONE_VALUE = object {
            // Note: toString() for debugger
            override fun toString(): String = "None"
        }
        private val NONE = Maybe<Nothing>(NONE_VALUE)

        fun none(): Maybe<Nothing> = NONE

        fun <T> some(value: T): Maybe<T> = Maybe(value)

        fun <T : Any> from(value: T?): Maybe<T> = if (value == null) none() else some(value)
    }
}

inline fun <T, reified R> Maybe<T>.map(body: (T) -> Maybe<R>): Maybe<R> =
    if (isNone) Maybe.none() else body(getOrThrow())

inline fun <T, reified R> Maybe<T>.fmap(body: (T) -> R): Maybe<R> =
    if (isNone) Maybe.none() else Maybe.some(body(getOrThrow()))

inline fun <T> Maybe<T>.onSome(body: (T) -> Unit): Maybe<T> {
    if (isSome) body(getOrThrow())
    return this
}

inline fun <T> Maybe<T>.onNone(body: () -> Unit): Maybe<T> {
    if (isNone) body()
    return this
}

fun <T : Any> T?.toMaybe(): Maybe<T> = Maybe.from(this)
