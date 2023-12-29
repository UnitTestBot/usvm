package org.usvm.api

import io.ksmt.utils.asExpr
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.GoContext
import org.usvm.machine.interpreter.GoStepScope
import org.usvm.machine.state.GoMethodResult
import org.usvm.memory.URegisterStackLValue
import java.nio.ByteBuffer

class Api(
    private val ctx: GoContext,
    private val scope: GoStepScope,
) {
    fun mk(buf: ByteBuffer, setLastBlock: Boolean) {
        when (Method.valueOf(buf.get())) {
            Method.MK_UN_OP -> mkUnOp(buf)
            Method.MK_BIN_OP -> mkBinOp(buf)
            Method.MK_CALL -> mkCall(buf)
            Method.MK_IF -> mkIf(buf)
            Method.MK_RETURN -> mkReturn(buf)
            Method.MK_VARIABLE -> mkVariable(buf)
            Method.UNKNOWN -> buf.rewind()
        }

        if (setLastBlock) {
            setLastBlock(buf.int)
        }
    }

    private fun mkUnOp(buf: ByteBuffer) {
        val op = UnOp.valueOf(buf.get())
        val sort = toSort(Type.valueOf(buf.get()))
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val x = resolveVar(buf)

        val expr = when (op) {
            UnOp.RECV -> TODO()
            UnOp.NEG -> ctx.mkBvNegationExpr(bv(x))
            UnOp.DEREF -> TODO()
            UnOp.NOT -> ctx.mkNot(x.asExpr(ctx.boolSort))
            UnOp.INV -> ctx.mkBvNotExpr(bv(x))
            else -> throw UnknownUnaryOperationException()
        }

        val lvalue = URegisterStackLValue(sort, idx)
        scope.doWithState {
            memory.write(lvalue, expr.asExpr(sort), ctx.trueExpr)
        }
    }

    private fun mkBinOp(buf: ByteBuffer) {
        val op = BinOp.valueOf(buf.get())
        val type = Type.valueOf(buf.get())
        val signed = type.isSigned()
        val sort = toSort(type)
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val x = resolveVar(buf)
        val y = resolveVar(buf)

        val expr = when (op) {
            BinOp.ADD -> ctx.mkBvAddExpr(bv(x), bv(y))
            BinOp.SUB -> ctx.mkBvSubExpr(bv(x), bv(y))
            BinOp.MUL -> ctx.mkBvMulExpr(bv(x), bv(y))
            BinOp.DIV -> {
                val l = bv(x)
                val r = bv(y)
                if (signed) {
                    ctx.mkBvSignedDivExpr(l, r)
                } else {
                    ctx.mkBvUnsignedDivExpr(l, r)
                }
            }

            BinOp.MOD -> ctx.mkBvSignedModExpr(bv(x), bv(y))
            BinOp.AND -> ctx.mkBvAndExpr(bv(x), bv(y))
            BinOp.OR -> ctx.mkBvOrExpr(bv(x), bv(y))
            BinOp.XOR -> ctx.mkBvXorExpr(bv(x), bv(y))
            BinOp.SHL -> ctx.mkBvShiftLeftExpr(bv(x), bv(y))
            BinOp.SHR -> ctx.mkBvArithShiftRightExpr(bv(x), bv(y))
            BinOp.AND_NOT -> TODO()
            BinOp.EQ -> ctx.mkEq(x, y)
            BinOp.LT -> {
                val l = bv(x)
                val r = bv(y)
                if (signed) {
                    ctx.mkBvSignedLessExpr(l, r)
                } else {
                    ctx.mkBvUnsignedLessExpr(l, r)
                }
            }

            BinOp.GT -> {
                val l = bv(x)
                val r = bv(y)
                if (signed) {
                    ctx.mkBvSignedGreaterExpr(l, r)
                } else {
                    ctx.mkBvUnsignedGreaterExpr(l, r)
                }
            }

            BinOp.NEQ -> ctx.mkNot(ctx.mkEq(x, y))
            BinOp.LE -> {
                val l = bv(x)
                val r = bv(y)
                if (signed) {
                    ctx.mkBvSignedLessOrEqualExpr(l, r)
                } else {
                    ctx.mkBvUnsignedLessOrEqualExpr(l, r)
                }
            }

            BinOp.GE -> {
                val l = bv(x)
                val r = bv(y)
                if (signed) {
                    ctx.mkBvSignedGreaterOrEqualExpr(l, r)
                } else {
                    ctx.mkBvUnsignedGreaterOrEqualExpr(l, r)
                }
            }

            else -> throw UnknownBinaryOperationException()
        }

        val lvalue = URegisterStackLValue(sort, idx)
        scope.doWithState {
            memory.write(lvalue, expr.asExpr(sort), ctx.trueExpr)
        }
    }

    private fun mkCall(buf: ByteBuffer) {
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val result = scope.calcOnState { methodResult }
        if (result is GoMethodResult.Success) {
            scope.doWithState {
                val lvalue = URegisterStackLValue(result.value.sort, idx)
                memory.write(lvalue, result.value, ctx.trueExpr)
                methodResult = GoMethodResult.NoCall
            }
            return
        }

        val method = buf.long
        val entrypoint = buf.long
        val parametersCount = buf.int
        val localsCount = buf.int
        val parameters = Array<UExpr<out USort>>(parametersCount) {
            resolveVar(buf)
        }
        ctx.setArgsCount(method, parametersCount)

        scope.doWithState {
            callStack.push(method, currentStatement)
            memory.stack.push(parameters, localsCount)
            newInst(entrypoint)
        }
    }

    private fun mkIf(buf: ByteBuffer) {
        val expression = resolveVar(buf)
        val pos = buf.long
        val neg = buf.long
        scope.forkWithBlackList(
            expression.asExpr(ctx.boolSort),
            pos,
            neg,
            blockOnTrueState = { newInst(pos) },
            blockOnFalseState = { newInst(neg) }
        )
    }

    private fun mkReturn(buf: ByteBuffer) {
        val value = resolveVar(buf)
        scope.doWithState {
            returnValue(value)
        }
    }

    private fun mkVariable(buf: ByteBuffer) {
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val rvalue = resolveVar(buf)
        val lvalue = URegisterStackLValue(rvalue.sort, idx)
        scope.doWithState {
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun resolveVar(buf: ByteBuffer): UExpr<USort> {
        val kind = VarKind.valueOf(buf.get())
        val type = Type.valueOf(buf.get())
        val sort = toSort(type)
        val expr = when (kind) {
            VarKind.CONST -> resolveConst(buf, type)
            VarKind.PARAMETER, VarKind.LOCAL -> scope.calcOnState {
                memory.read(URegisterStackLValue(sort, resolveIndex(kind, buf.int)))
            }

            else -> throw UnknownVarKindException()
        }
        return expr.asExpr(sort)
    }

    private fun resolveConst(buf: ByteBuffer, type: Type): UExpr<out USort> = when (type) {
        Type.BOOL -> ctx.mkBool(buf.get() == 1.toByte())
        Type.INT8, Type.UINT8 -> ctx.mkBv(buf.get(), ctx.bv8Sort)
        Type.INT16, Type.UINT16 -> ctx.mkBv(buf.short, ctx.bv16Sort)
        Type.INT32, Type.UINT32 -> ctx.mkBv(buf.int, ctx.bv32Sort)
        Type.INT64, Type.UINT64 -> ctx.mkBv(buf.long, ctx.bv64Sort)
        Type.FLOAT32 -> ctx.mkFp(buf.float, ctx.fp32Sort)
        Type.FLOAT64 -> ctx.mkFp(buf.double, ctx.fp64Sort)
        else -> throw UnknownTypeException()
    }

    private fun resolveIndex(kind: VarKind, value: Int): Int = when (kind) {
        VarKind.LOCAL -> value + ctx.getArgsCount(scope.calcOnState { lastEnteredMethod })
        VarKind.PARAMETER -> value
        else -> -1
    }

    private fun toSort(type: Type): USort = when (type) {
        Type.BOOL -> ctx.boolSort
        Type.INT8, Type.UINT8 -> ctx.bv8Sort
        Type.INT16, Type.UINT16 -> ctx.bv16Sort
        Type.INT32, Type.UINT32 -> ctx.bv32Sort
        Type.INT64, Type.UINT64 -> ctx.bv64Sort
        Type.FLOAT32 -> ctx.fp32Sort
        Type.FLOAT64 -> ctx.fp64Sort
        else -> throw UnknownSortException()
    }

    private fun bv(expr: UExpr<USort>): UExpr<UBvSort> {
        return expr.asExpr(expr.sort as UBvSort)
    }

    fun getLastBlock(): Int {
        return scope.calcOnState { lastBlock }
    }

    private fun setLastBlock(block: Int) {
        scope.doWithState { lastBlock = block }
    }
}

private enum class Method(val value: Byte) {
    UNKNOWN(0),
    MK_UN_OP(1),
    MK_BIN_OP(2),
    MK_CALL(3),
    MK_IF(4),
    MK_RETURN(5),
    MK_VARIABLE(6);

    companion object {
        private val values = values()

        fun valueOf(value: Byte) = values.firstOrNull { it.value == value } ?: throw UnknownMethodException()
    }
}

private enum class UnOp(val value: Byte) {
    ILLEGAL(0),
    RECV(1),
    NEG(2),
    DEREF(3),
    NOT(4),
    INV(5);

    companion object {
        private val values = values()

        fun valueOf(value: Byte) = values.firstOrNull { it.value == value } ?: throw UnknownUnaryOperationException()
    }
}

private enum class BinOp(val value: Byte) {
    ILLEGAL(0),
    ADD(1),
    SUB(2),
    MUL(3),
    DIV(4),
    MOD(5),
    AND(6),
    OR(7),
    XOR(8),
    SHL(9),
    SHR(10),
    AND_NOT(11),
    EQ(12),
    LT(13),
    GT(14),
    NEQ(15),
    LE(16),
    GE(17);

    companion object {
        private val values = values()

        fun valueOf(value: Byte) = values.firstOrNull { it.value == value } ?: throw UnknownBinaryOperationException()
    }
}

private enum class VarKind(val value: Byte) {
    ILLEGAL(0),
    CONST(1),
    PARAMETER(2),
    LOCAL(3);

    companion object {
        private val values = values()

        fun valueOf(value: Byte) = values.firstOrNull { it.value == value } ?: throw UnknownVarKindException()
    }
}

private enum class Type(val value: Byte) {
    UNKNOWN(0),
    BOOL(1),
    INT8(2),
    UINT8(3),
    INT16(4),
    UINT16(5),
    INT32(6),
    UINT32(7),
    INT64(8),
    UINT64(9),
    FLOAT32(10),
    FLOAT64(11);

    fun isSigned(): Boolean = when (this) {
        BOOL, INT8, INT16, INT32, INT64, FLOAT32, FLOAT64 -> true
        UINT8, UINT16, UINT32, UINT64 -> false
        else -> false
    }

    companion object {
        private val values = values()

        fun valueOf(value: Byte) = values.firstOrNull { it.value == value } ?: throw UnknownTypeException()
    }
}
