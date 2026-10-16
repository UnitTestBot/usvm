package org.usvm.ts.pbt.fastcheck

import org.usvm.ts.pbt.model.JsConcreteValue
import java.util.IdentityHashMap

/** Validates the supported wire value tree without using serializer failures to select an admission outcome. */
internal fun List<JsConcreteValue>.hasWitnessEncoding(): Boolean {
    val pending = ArrayDeque<JsConcreteValue>()
    val seen = IdentityHashMap<JsConcreteValue, Boolean>()
    pending.addAll(this)

    while (pending.isNotEmpty()) {
        when (val value = pending.removeLast()) {
            is JsConcreteValue.Number -> {
                if (!value.number.hasValidEncoding()) return false
            }
            is JsConcreteValue.Array -> {
                if (seen.put(value, true) != null) return false

                pending.addAll(value.elements)
            }

            is JsConcreteValue.Object -> {
                val hasUnsupportedFields = value.fields.keys.any { name -> name in UNSUPPORTED_OBJECT_FIELDS }
                if (hasUnsupportedFields || seen.put(value, true) != null) return false

                pending.addAll(value.fields.values)
            }

            JsConcreteValue.Null,
            JsConcreteValue.Undefined,
            is JsConcreteValue.Boolean,
            is JsConcreteValue.String,
            -> {
                Unit
            }
        }
    }

    return true
}

private val UNSUPPORTED_OBJECT_FIELDS = hashSetOf("__proto__", "constructor", "prototype")
