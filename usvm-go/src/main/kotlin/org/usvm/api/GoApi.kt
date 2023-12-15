package org.usvm.api

import com.sun.jna.Callback
import io.ksmt.utils.asExpr
import org.usvm.UBv32Sort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.bridge.Inst
import org.usvm.domain.GoInst
import org.usvm.machine.GoContext
import org.usvm.machine.interpreter.GoStepScope
import org.usvm.memory.URegisterStackLValue

class GoApi(
    private val ctx: GoContext,
    private val scope: GoStepScope,
) {
    fun mkIntRegisterReading(idx: Int) {
        ctx.mkRegisterReading(idx, ctx.bv32Sort)
    }

    fun mkLess(name: String, fst: String, snd: String) {
        mkBinOp(name, fst, snd, ctx.bv32Sort, ctx.boolSort, ::mkIntConst, ctx::mkBvSignedLessExpr)
    }

    fun mkGreater(name: String, fst: String, snd: String) {
        mkBinOp(name, fst, snd, ctx.bv32Sort, ctx.boolSort, ::mkIntConst, ctx::mkBvSignedGreaterExpr)
    }

    fun mkAdd(name: String, fst: String, snd: String) {
        mkBinOp(name, fst, snd, ctx.bv32Sort, ctx.bv32Sort, ::mkIntConst, ctx::mkBvAddExpr)
    }

    fun mkIf(name: String, posInst: GoInst, negInst: GoInst) {
        val lvalue = URegisterStackLValue(ctx.boolSort, ctx.idx(name))
        val expression = scope.calcOnState { memory.read(lvalue) }.asExpr(ctx.boolSort)
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

    fun mkReturn(name: String) {
        scope.doWithState {
            val value = memory.read(URegisterStackLValue(ctx.bv32Sort, ctx.idx(name)))
            returnValue(value)
        }
    }

    private fun <In : USort, Out : USort> mkBinOp(
        name: String,
        fst: String,
        snd: String,
        inSort: In,
        outSort: Out,
        mkConst: (f: String) -> UExpr<In>?,
        mkExpr: (f: UExpr<In>, s: UExpr<In>) -> UExpr<Out>
    ) {
        val f = mkConst(fst) ?: scope.calcOnState {
            memory.read(URegisterStackLValue(inSort, ctx.idx(fst)))
        }.asExpr(inSort)
        val s = mkConst(snd) ?: scope.calcOnState {
            memory.read(URegisterStackLValue(inSort, ctx.idx(snd)))
        }.asExpr(inSort)
        val lvalue = URegisterStackLValue(outSort, ctx.idx(name))
        scope.doWithState {
            memory.write(lvalue, mkExpr(f, s).asExpr(outSort), ctx.trueExpr)
        }
    }

    private fun mkIntConst(expr: String): UExpr<UBv32Sort>? {
        expr.split(":").let { s ->
            if (s.isEmpty()) {
                return null
            }
            try {
                return ctx.mkBv(s[0].toInt())
            } catch (e: Exception) {
                return null
            }
        }
    }
}

interface MkIntRegisterReading : Callback {
    fun mkIntRegisterReading(name: String, idx: Int)
}

interface MkLess : Callback {
    fun mkLess(name: String, fst: String, snd: String)
}

interface MkGreater : Callback {
    fun mkGreater(name: String, fst: String, snd: String)
}

interface MkAdd : Callback {
    fun mkAdd(name: String, fst: String, snd: String)
}

interface MkIf : Callback {
    fun mkIf(name: String, posInst: Inst.ByValue, negInst: Inst.ByValue)
}

interface MkReturn : Callback {
    fun mkReturn(name: String)
}
