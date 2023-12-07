package org.usvm.api

import com.sun.jna.Callback
import org.usvm.bridge.Inst
import org.usvm.machine.GoContext
import org.usvm.machine.GoInst
import org.usvm.machine.interpreter.GoStepScope

class GoApi(
    private val ctx: GoContext,
    private val scope: GoStepScope,
) : MkIntRegisterReading, MkIntSignedLessExpr, MkIntSignedGreaterExpr, MkIfInst, MkReturnInst {
    override fun mkIntRegisterReading(name: String, idx: Int) {
        ctx.vars[name] = ctx.mkRegisterReading(idx, ctx.bv32Sort)
    }

    override fun mkIntSignedLessExpr(fst: String, snd: String) {
        val f = ctx.vars[fst] ?: return
        val s = ctx.vars[snd] ?: return
        ctx.expressions["$fst < $snd"] = ctx.mkBvSignedLessExpr(f, s)
    }

    override fun mkIntSignedGreaterExpr(fst: String, snd: String) {
        val f = ctx.vars[fst] ?: return
        val s = ctx.vars[snd] ?: return
        ctx.expressions["$fst > $snd"] = ctx.mkBvSignedGreaterExpr(f, s)
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
    }

    override fun mkReturnInst(name: String) {
        val value = ctx.vars[name] ?: return
        scope.doWithState {
            returnValue(value)
        }
    }
}

interface MkIntRegisterReading : Callback {
    fun mkIntRegisterReading(name: String, idx: Int)
}

interface MkIntSignedLessExpr : Callback {
    fun mkIntSignedLessExpr(fst: String, snd: String)
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