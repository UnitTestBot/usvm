package org.usvm.api

import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsStmt

// Helper to indent multi-line strings consistently
private fun String.indentLines(spaces: Int): String {
    if (this.isEmpty()) return this
    val pad = " ".repeat(spaces)
    return this.lineSequence().joinToString("\n") { pad + it }
}

data class TsTest(
    val method: EtsMethod,
    val before: TsParametersState,
    val after: TsParametersState,
    val returnValue: TsTestValue,
    val trace: List<EtsStmt>? = null,
) {
    override fun toString(): String {
        val methodLine = "Test for method: ${method.enclosingClass?.signature ?: ""}.${method.name}"
        val beforeBlock = buildString {
            appendLine("Before state:")
            append(before.toString().indentLines(2))
        }
        val afterBlock = buildString {
            appendLine("After state:")
            append(after.toString().indentLines(2))
        }
        val traceBlock = buildString {
            appendLine("Trace:")
            if (trace.isNullOrEmpty()) {
                append("  (none)")
            } else {
                val traceLines = trace.joinToString("\n") { "- $it" }
                append(traceLines.indentLines(2))
            }
        }
        val block = listOf(methodLine, beforeBlock, afterBlock, "Return value: $returnValue", traceBlock)
            .joinToString("\n")
        return block
    }
}

data class TsParametersState(
    val thisInstance: TsTestValue?,
    val parameters: List<TsTestValue>,
    val globals: Map<EtsClass, List<GlobalFieldValue>>,
) {
    override fun toString(): String {
        val paramsBlock = buildString {
            appendLine("Parameters:")
            if (parameters.isEmpty()) {
                append("  (none)")
            } else {
                parameters.forEachIndexed { idx, v -> appendLine("  ${idx + 1}. $v") }
            }
        }
        val globalsBlock = buildString {
            appendLine("Globals:")
            if (globals.isEmpty()) {
                append("  (none)")
            } else {
                globals.entries
                    .sortedBy { it.key.name }
                    .forEach { (cls, fields) ->
                        appendLine("  ${cls.name}:")
                        fields.sortedBy { it.field.name }
                            .forEach { fv -> appendLine("    - ${fv.field.name}=${fv.value}") }
                    }
            }
        }
        return buildString {
            appendLine("Parameters State:")
            appendLine("  This instance: ${thisInstance ?: "null"}")
            appendLine(paramsBlock.indentLines(2))
            append(globalsBlock.indentLines(2))
        }.trimEnd()
    }
}

data class GlobalFieldValue(val field: EtsField, val value: TsTestValue) // TODO is it right?????

open class TsMethodCoverage

object NoCoverage : TsMethodCoverage()

sealed interface TsTestValue {
    data object TsAny : TsTestValue
    data object TsUnknown : TsTestValue
    data object TsNull : TsTestValue
    data object TsUndefined : TsTestValue
    data object TsException : TsTestValue

    data class TsBoolean(val value: Boolean) : TsTestValue

    data class TsString(val value: String) : TsTestValue {
        override fun toString(): String {
            return "${this::class.simpleName}(value=\"$value\")"
        }
    }

    data class TsBigInt(val value: String) : TsTestValue

    sealed interface TsNumber : TsTestValue {
        data class TsInteger(val value: Int) : TsNumber

        data class TsDouble(val value: Double) : TsNumber

        val number: Double
            get() = when (this) {
                is TsInteger -> value.toDouble()
                is TsDouble -> value
            }
    }

    data class TsClass(
        val name: String,
        val properties: Map<String, TsTestValue>,
    ) : TsTestValue

    data class TsArray<T : TsTestValue>(
        val values: List<T>,
    ) : TsTestValue
}
