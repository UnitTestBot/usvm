package org.usvm.machine

import org.jacodb.ets.model.EtsMethod

/**
 * Runtime type tags observed for input values during a concrete (e.g. PBT) phase.
 * Used to constrain the symbolic search over dynamically-typed inputs.
 */
enum class TsHintType {
    NUMBER,
    BOOLEAN,
    STRING,
    NULL,
    UNDEFINED,
    OBJECT,
    ARRAY,
}

/**
 * Observed input type profiles: method key -> (parameter index -> observed type tags).
 *
 * When a parameter's declared type is unresolved (any/unknown/union), the interpreter
 * normally creates a *fake object* whose type discriminators are unconstrained,
 * which multiplies the search space. Hints restrict the discriminators to the
 * observed set (see `TsInterpreter.getInitialState`).
 *
 * The hints are an *unsound* optimization by design: a fallback run without hints
 * is expected at the orchestration level when a target is not reached with them.
 */
data class TsInputTypeHints(
    val byMethod: Map<String, Map<Int, Set<TsHintType>>> = emptyMap(),
) {
    fun forParameter(method: EtsMethod, index: Int): Set<TsHintType>? =
        byMethod[keyOf(method)]?.get(index)?.takeIf { it.isNotEmpty() }

    companion object {
        val EMPTY = TsInputTypeHints()

        /** The single canonical key shared by hint producers and consumers. */
        fun keyOf(method: EtsMethod): String = method.signature.toString()
    }
}
