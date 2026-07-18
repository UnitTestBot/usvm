package org.usvm.ts.pbt.interpreter

/**
 * Outcome of one concrete execution of an [org.jacodb.ets.model.EtsMethod].
 *
 * The default PBT property is "the result is not [Threw]".
 * [Diverged] and [Unsupported] are *neither* a pass nor a failure: they are
 * reported separately so that the experiment numbers stay honest.
 */
sealed interface ExecutionResult {
    data class Returned(val value: VValue) : ExecutionResult

    /** A JS-level `throw` (including modeled TypeErrors, e.g. field access on undefined). */
    data class Threw(val value: VValue) : ExecutionResult

    /** Execution exceeded [ExecutionLimits] (step budget / call depth). */
    data class Diverged(val reason: String) : ExecutionResult

    /** The interpreter hit an IR construct or intrinsic it does not model. */
    data class Unsupported(val reason: String) : ExecutionResult
}

data class ExecutionLimits(
    val maxSteps: Long = 100_000,
    val maxCallDepth: Int = 64,
    /** Largest dense JS array the concrete interpreter will materialize. */
    val maxArrayLength: Int = 1_000,
)

private const val JS_MAX_ARRAY_LENGTH = 4_294_967_295.0

/**
 * Validate JavaScript Array-length semantics separately from our materialization
 * budget. Invalid JS lengths throw RangeError; valid but impractically large
 * arrays exhaust the concrete execution budget instead of crashing the JVM.
 */
internal fun checkedConcreteArrayLength(value: Double, limit: Int): Int {
    if (!value.isFinite() || value < 0 || value != kotlin.math.floor(value) || value > JS_MAX_ARRAY_LENGTH) {
        throw JsThrowSignal(VString("RangeError: Invalid array length"))
    }
    if (value > limit) {
        throw BudgetExceededSignal("array length ${JsSemantics.numberToString(value)} exceeds concrete limit $limit")
    }
    return value.toInt()
}

/** Internal control-flow signal: JS `throw`. */
internal class JsThrowSignal(val value: VValue) : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

/** Internal control-flow signal: unsupported IR construct. */
internal class UnsupportedFeatureSignal(val reason: String) : RuntimeException(reason) {
    override fun fillInStackTrace(): Throwable = this
}

/** Internal control-flow signal: execution budget exceeded. */
internal class BudgetExceededSignal(val reason: String) : RuntimeException(reason) {
    override fun fillInStackTrace(): Throwable = this
}

internal fun typeError(message: String): JsThrowSignal =
    JsThrowSignal(
        VObject(
            cls = null,
            fields = mutableMapOf(
                "name" to VString("TypeError"),
                "message" to VString(message),
            ),
        )
    )
