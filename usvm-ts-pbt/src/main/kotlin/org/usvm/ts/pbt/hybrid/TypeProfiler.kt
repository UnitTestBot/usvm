package org.usvm.ts.pbt.hybrid

import org.jacodb.ets.model.EtsMethod
import org.usvm.machine.TsHintType
import org.usvm.machine.TsInputTypeHints
import org.usvm.ts.pbt.interpreter.ExecutionListener
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
 * Records the runtime types of method inputs observed during the concrete (PBT)
 * phase. The result feeds the symbolic phase as [TsInputTypeHints].
 *
 * NOTE: profiles reflect the *generator's* type distribution for the entry method
 * (every generated type shows up). Their pruning power comes from methods whose
 * parameters have declared/inferable structure, and from *call-site* profiles of
 * transitively invoked methods, where observed types reflect actual data flow.
 */
class TypeProfiler : ExecutionListener {

    private val observed = mutableMapOf<String, MutableMap<Int, MutableSet<TsHintType>>>()

    override fun onMethodEnter(method: EtsMethod, thisValue: VValue, args: List<VValue>) {
        val perParam = observed.getOrPut(TsInputTypeHints.keyOf(method)) { mutableMapOf() }
        args.forEachIndexed { i, arg ->
            perParam.getOrPut(i) { mutableSetOf() } += tagOf(arg)
        }
    }

    fun toHints(): TsInputTypeHints =
        TsInputTypeHints(observed.mapValues { (_, m) -> m.mapValues { (_, s) -> s.toSet() } })

    companion object {
        fun tagOf(v: VValue): TsHintType = when (v) {
            is VNumber -> TsHintType.NUMBER
            is VBool -> TsHintType.BOOLEAN
            is VString -> TsHintType.STRING
            VNull -> TsHintType.NULL
            VUndefined -> TsHintType.UNDEFINED
            is VArray -> TsHintType.ARRAY
            is VObject, is VNamespace -> TsHintType.OBJECT
        }
    }
}
