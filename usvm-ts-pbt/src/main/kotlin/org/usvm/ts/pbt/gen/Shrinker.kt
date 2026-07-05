package org.usvm.ts.pbt.gen

import org.usvm.ts.pbt.interpreter.VArray
import org.usvm.ts.pbt.interpreter.VBool
import org.usvm.ts.pbt.interpreter.VNumber
import org.usvm.ts.pbt.interpreter.VObject
import org.usvm.ts.pbt.interpreter.VString
import org.usvm.ts.pbt.interpreter.VUndefined
import org.usvm.ts.pbt.interpreter.VValue
import kotlin.math.abs
import kotlin.math.floor

/**
 * Greedy per-parameter shrinking: repeatedly try simpler candidate values,
 * keep any replacement under which the property still fails, until a fixpoint
 * or the budget is exhausted.
 */
class Shrinker(
    private val maxAttempts: Int = 500,
) {
    /**
     * @param stillFails re-runs the property with the candidate inputs;
     *   `true` = the failure is preserved.
     */
    fun shrink(args: List<VValue>, stillFails: (List<VValue>) -> Boolean): List<VValue> {
        var current = args
        var attempts = 0
        var progress = true

        while (progress && attempts < maxAttempts) {
            progress = false
            for (i in current.indices) {
                for (candidate in candidates(current[i])) {
                    if (attempts++ >= maxAttempts) return current
                    val next = current.toMutableList().also { it[i] = candidate }
                    if (stillFails(next)) {
                        current = next
                        progress = true
                        break
                    }
                }
            }
        }
        return current
    }

    private fun candidates(v: VValue): List<VValue> = when (v) {
        is VNumber -> buildList {
            if (v.value != 0.0 || v.value.isNaN()) add(VNumber(0.0))
            if (v.value.isNaN() || v.value.isInfinite()) add(VNumber(1.0))
            if (v.value.isFinite() && v.value != floor(v.value)) add(VNumber(floor(v.value)))
            if (v.value.isFinite() && abs(v.value) > 1) add(VNumber(v.value / 2))
            if (v.value < 0 && v.value.isFinite()) add(VNumber(-v.value))
        }.filter { it != v }

        is VString -> buildList {
            if (v.value.isNotEmpty()) {
                add(VString(""))
                add(VString(v.value.substring(0, v.value.length / 2)))
                add(VString(v.value.drop(1)))
            }
        }.filter { it != v }

        is VBool -> if (v.value) listOf(VBool(false)) else emptyList()

        is VArray -> buildList {
            if (v.elements.isNotEmpty()) {
                add(VArray(mutableListOf()))
                add(VArray(v.elements.subList(0, v.elements.size / 2).toMutableList()))
                add(VArray(v.elements.subList(1, v.elements.size).toMutableList()))
            }
        }

        is VObject -> buildList {
            if (v.fields.isNotEmpty()) {
                // Try emptying the fields, then undefined-ing them one by one
                add(VObject(v.cls, mutableMapOf()))
                for (key in v.fields.keys) {
                    val reduced = v.fields.toMutableMap()
                    reduced[key] = VUndefined
                    add(VObject(v.cls, reduced))
                }
            }
        }

        else -> emptyList() // VNull/VUndefined/VNamespace are already minimal
    }
}
