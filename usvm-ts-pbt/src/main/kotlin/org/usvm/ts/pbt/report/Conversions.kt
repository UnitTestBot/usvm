package org.usvm.ts.pbt.report

import org.jacodb.ets.model.EtsClass
import org.usvm.api.TsTestValue
import org.usvm.ts.pbt.interpreter.VArray
import org.usvm.ts.pbt.interpreter.VBool
import org.usvm.ts.pbt.interpreter.VNamespace
import org.usvm.ts.pbt.interpreter.VNull
import org.usvm.ts.pbt.interpreter.VNumber
import org.usvm.ts.pbt.interpreter.VObject
import org.usvm.ts.pbt.interpreter.VString
import org.usvm.ts.pbt.interpreter.VUndefined
import org.usvm.ts.pbt.interpreter.VValue

/**
 * Placeholder emitted by `org.usvm.util.TsTestResolver` for symbolic strings it
 * cannot concretize. Values carrying it cannot be replayed faithfully.
 */
const val SYMBOLIC_STRING_PLACEHOLDER: String = "String construction is not yet implemented"

/**
 * Convert a symbolic-phase test value ([TsTestValue], extracted from a usvm-ts model)
 * into a concrete interpreter value.
 *
 * @return `null` if the value cannot be represented faithfully (then the replay
 *   must be skipped and coverage recovered from the symbolic trace instead).
 */
fun TsTestValue.toVValueOrNull(classResolver: (String) -> EtsClass? = { null }): VValue? {
    return when (this) {
        is TsTestValue.TsNumber -> VNumber(number)
        is TsTestValue.TsBoolean -> VBool(value)
        is TsTestValue.TsString ->
            if (value == SYMBOLIC_STRING_PLACEHOLDER) null else VString(value)

        TsTestValue.TsNull -> VNull
        TsTestValue.TsUndefined -> VUndefined

        is TsTestValue.TsArray<*> -> {
            val elements = values.map { it.toVValueOrNull(classResolver) ?: return null }
            VArray(elements.toMutableList())
        }

        is TsTestValue.TsClass -> {
            val fields = mutableMapOf<String, VValue>()
            for ((k, v) in properties) {
                fields[k] = v.toVValueOrNull(classResolver) ?: return null
            }
            VObject(classResolver(name), fields)
        }

        // Unknowns, BigInt, exceptions: not replayable
        else -> null
    }
}

/** Convert a concrete interpreter value into the reporting format shared with usvm-ts. */
fun VValue.toTsTestValue(): TsTestValue = when (this) {
    is VNumber -> TsTestValue.TsNumber.TsDouble(value)
    is VBool -> TsTestValue.TsBoolean(value)
    is VString -> TsTestValue.TsString(value)
    VNull -> TsTestValue.TsNull
    VUndefined -> TsTestValue.TsUndefined
    is VArray -> TsTestValue.TsArray(elements.map { it.toTsTestValue() })
    is VObject -> TsTestValue.TsClass(
        name = cls?.name ?: "Object",
        properties = fields.mapValues { (_, v) -> v.toTsTestValue() },
    )

    is VNamespace -> TsTestValue.TsClass(name = name, properties = emptyMap())
}
