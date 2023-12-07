package org.usvm.api

import com.sun.jna.Callback
import org.usvm.bridge.Inst
import org.usvm.machine.GoContext
import org.usvm.machine.GoInst
import org.usvm.machine.interpreter.GoStepScope

class GoApi(
    private val ctx: GoContext,
    private val scope: GoStepScope,
) : MkIntRegisterReading, MkIntSignedGreaterExpr, MkIfInst, MkReturnInst {
    override fun mkIntRegisterReading(name: String, idx: Int) {
        println("mkIntRegisterReading: $name $idx")

        ctx.vars[name] = ctx.mkRegisterReading(idx, ctx.bv32Sort)
    }

    override fun mkIntSignedGreaterExpr(fst: String, snd: String) {
        val f = ctx.vars[fst] ?: return
        val s = ctx.vars[snd] ?: return
        ctx.expressions["$fst > $snd"] = ctx.mkBvSignedGreaterExpr(f, s)
        println("mkIntSignedGreaterExpr: $fst $snd")
    }

    override fun mkIfInst(expr: String, posInst: Inst.ByValue, negInst: Inst.ByValue) {
        val expression = ctx.expressions[expr] ?: return
        val pos = GoInst(posInst.pointer, posInst.statement)
        val neg = GoInst(negInst.pointer, negInst.statement)
        scope.forkWithBlackList(
            expression,
            pos,
            neg,
            blockOnTrueState = { newInst(pos) },
            blockOnFalseState = { newInst(neg) }
        )
        println("mkIfInst: $expr $posInst $negInst")
    }

    override fun mkReturnInst(name: String) {
        val value = ctx.vars[name] ?: return
        scope.doWithState {
            returnValue(value)
        }
        println("mkReturnInst: $name $value")
    }
}

interface MkIntRegisterReading : Callback {
    fun mkIntRegisterReading(name: String, idx: Int)
}

interface MkIntSignedGreaterExpr : Callback {
    fun mkIntSignedGreaterExpr(fst: String, snd: String)
}

interface MkIfInst : Callback {
    fun mkIfInst(expr: String, posInst: Inst.ByValue, negInst: Inst.ByValue)
}

interface MkReturnInst : Callback {
    fun mkReturnInst(name: String)
}