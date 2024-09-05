package com.spbpu.bbfinfrastructure.mutator.mutations.java.util

abstract class ExpressionGenerator {

    private val MAX_TRIES = 3

    fun generateExpressionOfType(
        scope: List<ScopeComponent>,
        type: String
    ): String? {
        repeat(MAX_TRIES) {
            gen(scope, type)?.let { return it }
        }
        return null
    }

    protected abstract fun gen(scope: List<ScopeComponent>, type: String): String?
}