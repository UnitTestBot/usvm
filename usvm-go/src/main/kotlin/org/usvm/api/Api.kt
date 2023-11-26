package org.usvm.api

import io.ksmt.utils.asExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.GoContext
import org.usvm.machine.interpreter.GoStepScope
import org.usvm.memory.URegisterStackLValue
import java.nio.ByteBuffer

class Api(
    private val ctx: GoContext,
    private val scope: GoStepScope,
) {
    fun mk(args: ByteArray, setLastBlock: Boolean) {
        val buf = ByteBuffer.wrap(args)
        when (Method.valueOf(buf.get())) {
            Method.MK_BIN_OP -> mkBinOp(buf)
            Method.MK_IF -> mkIf(buf)
            Method.MK_RETURN -> mkReturn(buf)
            Method.MK_VARIABLE -> mkVariable(buf)
            Method.UNKNOWN -> buf.rewind()
        }

        if (setLastBlock) {
            setLastBlock(buf.int)
        }
    }

    fun getLastBlock(): Int {
        return scope.calcOnState { lastBlock }
    }

    private fun setLastBlock(block: Int) {
        scope.doWithState { lastBlock = block }
    }

    private fun mkBinOp(buf: ByteBuffer) {
        val op = BinOp.valueOf(buf.get())
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val fst = resolveVar(ctx.bv32Sort, VarKind.valueOf(buf.get()), buf.int)
        val snd = resolveVar(ctx.bv32Sort, VarKind.valueOf(buf.get()), buf.int)

        if (op == BinOp.ILLEGAL || fst == null || snd == null) {
            return
        }

        var outSort: USort = ctx.bv32Sort
        val expr = when (op) {
            BinOp.EQ -> {
                outSort = ctx.boolSort
                ctx.mkEq(fst, snd)
            }

            BinOp.NEQ -> {
                outSort = ctx.boolSort
                ctx.mkNot(ctx.mkEq(fst, snd))
            }

            BinOp.LT -> {
                outSort = ctx.boolSort
                ctx.mkBvSignedLessExpr(fst, snd)
            }

            BinOp.LE -> {
                outSort = ctx.boolSort
                ctx.mkBvSignedLessOrEqualExpr(fst, snd)
            }

            BinOp.GT -> {
                outSort = ctx.boolSort
                ctx.mkBvSignedGreaterExpr(fst, snd)
            }

            BinOp.GE -> {
                outSort = ctx.boolSort
                ctx.mkBvSignedGreaterOrEqualExpr(fst, snd)
            }

            BinOp.ADD -> {
                outSort = ctx.bv32Sort
                ctx.mkBvAddExpr(fst, snd)
            }

            BinOp.SUB -> {
                outSort = ctx.bv32Sort
                ctx.mkBvSubExpr(fst, snd)
            }

            BinOp.MUL -> {
                outSort = ctx.bv32Sort
                ctx.mkBvMulExpr(fst, snd)
            }

            BinOp.DIV -> {
                outSort = ctx.bv32Sort
                ctx.mkBvSignedDivExpr(fst, snd)
            }

            BinOp.MOD -> {
                outSort = ctx.bv32Sort
                ctx.mkBvSignedModExpr(fst, snd)
            }

            else -> null
        } ?: return

        val lvalue = URegisterStackLValue(outSort, idx)
        scope.doWithState {
            memory.write(lvalue, expr.asExpr(outSort), ctx.trueExpr)
        }
    }

    private fun mkIf(buf: ByteBuffer) {
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val lvalue = URegisterStackLValue(ctx.boolSort, idx)
        val expression = scope.calcOnState { memory.read(lvalue) }.asExpr(ctx.boolSort)
        val pos = buf.long
        val neg = buf.long
        scope.forkWithBlackList(
            expression,
            pos,
            neg,
            blockOnTrueState = { newInst(pos) },
            blockOnFalseState = { newInst(neg) }
        )
    }

    private fun mkReturn(buf: ByteBuffer) {
        val value = resolveVar(ctx.bv32Sort, VarKind.valueOf(buf.get()), buf.int) ?: return
        scope.doWithState {
            returnValue(value)
        }
    }

    private fun mkVariable(buf: ByteBuffer) {
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val lvalue = URegisterStackLValue(ctx.bv32Sort, idx)
        val rvalue = resolveVar(ctx.bv32Sort, VarKind.valueOf(buf.get()), buf.int) ?: return
        scope.doWithState {
            memory.write(lvalue, rvalue.asExpr(ctx.bv32Sort), ctx.trueExpr)
        }
    }

    private fun <Sort : USort> resolveVar(sort: Sort, kind: VarKind, value: Int): UExpr<Sort>? {
        val index = resolveIndex(kind, value)
        val expr = when (kind) {
            VarKind.CONST -> ctx.mkBv(value)
            VarKind.PARAMETER, VarKind.LOCAL -> scope.calcOnState { memory.read(URegisterStackLValue(sort, index)) }
            VarKind.ILLEGAL -> null
        }
        return expr?.asExpr(sort)
    }

    private fun resolveIndex(kind: VarKind, value: Int): Int = when (kind) {
        VarKind.LOCAL -> value + ctx.getArgsCount()
        VarKind.PARAMETER -> value
        else -> -1
    }
}

private enum class Method(val value: Byte) {
    UNKNOWN(0),
    MK_BIN_OP(1),
    MK_IF(2),
    MK_RETURN(3),
    MK_VARIABLE(4);

    companion object {
        fun valueOf(value: Byte) = Method.values().firstOrNull { it.value == value } ?: UNKNOWN
    }
}

private enum class BinOp(val value: Byte) {
    ILLEGAL(0),
    EQ(1),
    NEQ(2),
    LT(3),
    LE(4),
    GT(5),
    GE(6),
    ADD(7),
    SUB(8),
    MUL(9),
    DIV(10),
    MOD(11);

    companion object {
        fun valueOf(value: Byte) = BinOp.values().firstOrNull { it.value == value } ?: ILLEGAL
    }
}

private enum class VarKind(val value: Byte) {
    ILLEGAL(0),
    CONST(1),
    PARAMETER(2),
    LOCAL(3);

    companion object {
        fun valueOf(value: Byte) = VarKind.values().firstOrNull { it.value == value } ?: ILLEGAL
    }
}