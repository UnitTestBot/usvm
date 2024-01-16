package org.usvm.api

import io.ksmt.utils.asExpr
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UPointer
import org.usvm.USort
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapSize
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.machine.GoContext
import org.usvm.machine.GoInst
import org.usvm.machine.GoMethodInfo
import org.usvm.machine.USizeSort
import org.usvm.machine.interpreter.GoStepScope
import org.usvm.machine.state.GoMethodResult
import org.usvm.machine.type.Type
import org.usvm.memory.GoPointerLValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.sampleUValue
import org.usvm.sizeSort
import java.nio.ByteBuffer

class Api(
    private val ctx: GoContext,
    private val scope: GoStepScope,
) {
    fun mk(buf: ByteBuffer, inst: GoInst): GoInst {
        var nextInst = inst
        when (Method.valueOf(buf.get())) {
            Method.MK_UN_OP -> mkUnOp(buf)
            Method.MK_BIN_OP -> mkBinOp(buf)
            Method.MK_CALL -> mkCall(buf).let { if (it) nextInst = 0L }
            Method.MK_CALL_BUILTIN -> mkCallBuiltin(buf)
            Method.MK_STORE -> mkStore(buf)
            Method.MK_IF -> mkIf(buf)
            Method.MK_ALLOC -> mkHeapAlloc(buf)
            Method.MK_RETURN -> mkReturn(buf)
            Method.MK_VARIABLE -> mkVariable(buf)
            Method.MK_POINTER_FIELD_READING -> mkPointerFieldReading(buf)
            Method.MK_FIELD_READING -> mkFieldReading(buf)
            Method.MK_POINTER_ARRAY_READING -> mkPointerArrayReading(buf)
            Method.MK_ARRAY_READING -> mkArrayReading(buf)
            Method.MK_MAP_LOOKUP -> mkMapLookup(buf, nextInst)
            Method.MK_MAP_UPDATE -> mkMapUpdate(buf)
            Method.UNKNOWN -> buf.rewind()
        }

        if (nextInst != 0L) {
            setLastBlock(buf.int)
        }
        return nextInst
    }

    private fun mkUnOp(buf: ByteBuffer) {
        val op = UnOp.valueOf(buf.get())
        val sort = ctx.typeToSort(Type.valueOf(buf.get()))
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val x = readVar(buf)

        val expr = when (op) {
            UnOp.RECV -> TODO()
            UnOp.NEG -> ctx.mkBvNegationExpr(bv(x))
            UnOp.DEREF -> deref(x, sort)
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
        val sort = ctx.typeToSort(type)
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val x = readVar(buf)
        val y = readVar(buf)

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

    private fun mkCall(buf: ByteBuffer): Boolean {
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val result = scope.calcOnState { methodResult }
        if (result is GoMethodResult.Success) {
            scope.doWithState {
                val lvalue = URegisterStackLValue(result.value.sort, idx)
                memory.write(lvalue, result.value, ctx.trueExpr)
                methodResult = GoMethodResult.NoCall
            }
            return false
        }

        val method = buf.long
        val entrypoint = buf.long
        val methodInfo = readMethodInfo(buf)
        val parameters = Array<UExpr<out USort>>(methodInfo.parametersCount) {
            readVar(buf)
        }
        ctx.setMethodInfo(method, methodInfo)

        scope.doWithState {
            callStack.push(method, currentStatement)
            memory.stack.push(parameters, methodInfo.variablesCount)
            newInst(entrypoint)
        }

        return true
    }

    private fun mkCallBuiltin(buf: ByteBuffer) {
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val builtin = BuiltinFunction.valueOf(buf.get())
        val rvalue: UExpr<USort> = when (builtin) {
            BuiltinFunction.LEN -> {
                val kind = VarKind.valueOf(buf.get())
                val type = Type.valueOf(buf.get())
                scope.calcOnState {
                    val array = memory.read(URegisterStackLValue(ctx.addressSort, resolveIndex(kind, buf.int)))
                    memory.readArrayLength(array.asExpr(ctx.addressSort), type, ctx.sizeSort)
                }
            }

            else -> throw UnknownFunctionException()
        }
        val lvalue = URegisterStackLValue(rvalue.sort, idx)

        scope.doWithState {
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun mkStore(buf: ByteBuffer) {
        val ref = ctx.mkConcreteHeapRef((readVar(buf) as UPointer).address)
        val rvalue = readVar(buf)
        val lvalue = GoPointerLValue(ref, rvalue.sort)
        scope.doWithState {
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun mkIf(buf: ByteBuffer) {
        val expression = readVar(buf)
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

    private fun mkHeapAlloc(buf: ByteBuffer) {
        val kind = VarKind.valueOf(buf.get())
        val type = Type.valueOf(buf.get()).value.toLong()
        val idx = resolveIndex(kind, buf.int)

        val lvalue = URegisterStackLValue(ctx.pointerSort, idx)
        scope.doWithState {
            val ref = memory.allocConcrete(type)
            memory.write(lvalue, ctx.mkPointer(ref.address), ctx.trueExpr)
        }
    }

    private fun mkReturn(buf: ByteBuffer) {
        val value = readVar(buf)
        scope.doWithState {
            returnValue(value)
        }
    }

    private fun mkVariable(buf: ByteBuffer) {
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val rvalue = readVar(buf)
        val lvalue = URegisterStackLValue(rvalue.sort, idx)
        scope.doWithState {
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun mkPointerFieldReading(buf: ByteBuffer) {
        val obj = readFieldReading(buf) ?: return
        val lvalue = URegisterStackLValue(ctx.pointerSort, obj.varIndex)
        scope.doWithState {
            val ref = memory.allocConcrete(obj.type.value.toLong())
            val field = memory.read(UFieldLValue(obj.sort, obj.ref, obj.field))
            memory.write(GoPointerLValue(ref, obj.sort), field, ctx.trueExpr)
            memory.write(lvalue, ctx.mkPointer(ref.address), ctx.trueExpr)
        }
    }

    private fun mkFieldReading(buf: ByteBuffer) {
        val obj = readFieldReading(buf) ?: return
        val lvalue = URegisterStackLValue(obj.sort, obj.varIndex)
        val rvalue = scope.calcOnState {
            memory.readField(obj.ref, obj.field, obj.sort)
        }
        scope.doWithState {
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun mkPointerArrayReading(buf: ByteBuffer) {
        val arr = readArrayReading(buf) ?: return
        val lvalue = URegisterStackLValue(ctx.pointerSort, arr.varIndex)
        scope.doWithState {
            val ref = memory.allocConcrete(arr.type.value.toLong())
            val element = memory.read(UArrayIndexLValue(arr.sort, arr.ref, arr.index, Type.ARRAY))
            memory.write(GoPointerLValue(ref, arr.sort), element, ctx.trueExpr)
            memory.write(lvalue, ctx.mkPointer(ref.address), ctx.trueExpr)
        }
    }

    private fun mkArrayReading(buf: ByteBuffer) {
        val arr = readArrayReading(buf) ?: return
        val lvalue = URegisterStackLValue(arr.sort, arr.varIndex)
        val rvalue = scope.calcOnState {
            memory.readArrayIndex(arr.ref, arr.index, Type.ARRAY, arr.sort)
        }
        scope.doWithState {
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun mkMapLookup(buf: ByteBuffer, nextInst: GoInst) {
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val mapType = Type.MAP.value.toLong()
        val valueType = Type.valueOf(buf.get())

        val map = readVar(buf).asExpr(ctx.addressSort)
        val key = readVar(buf)

        checkNotNull(map) ?: return

        val valueSort = ctx.typeToSort(valueType)
        val lvalue = URegisterStackLValue(valueSort, idx)
        val rvalue = scope.calcOnState {
            memory.read(UMapEntryLValue(key.sort, valueSort, map, key, mapType, USizeExprKeyInfo()))
        }
        val contains = scope.calcOnState { memory.setContainsElement(map, key, mapType, USizeExprKeyInfo()) }

        scope.fork(
            contains,
            blockOnTrueState = {
                memory.write(lvalue, rvalue, ctx.trueExpr)
            },
            blockOnFalseState = {
                memory.write(lvalue, valueSort.sampleUValue(), ctx.trueExpr)
                newInst(nextInst)
            }
        )
    }

    private fun mkMapUpdate(buf: ByteBuffer) = with(ctx) {
        val mapType = Type.MAP.value.toLong()
        val map = readVar(buf).asExpr(ctx.addressSort)
        val key = readVar(buf)
        val value = readVar(buf)

        checkNotNull(map) ?: return

        scope.doWithState {
            val mapContainsLValue = USetEntryLValue(value.sort, map, key, mapType, USizeExprKeyInfo())
            val currentSize = symbolicObjectMapSize(map, mapType)

            val keyIsInMap = memory.read(mapContainsLValue)
            val keyIsNew = mkNot(keyIsInMap)

            memory.write(
                UMapEntryLValue(key.sort, value.sort, map, key, mapType, USizeExprKeyInfo()),
                value,
                guard = trueExpr
            )
            memory.write(mapContainsLValue, rvalue = trueExpr, guard = trueExpr)

            val updatedSize = mkSizeAddExpr(currentSize, mkSizeExpr(1))
            memory.write(UMapLengthLValue(map, mapType, sizeSort), updatedSize, keyIsNew)
        }
    }

    fun getLastBlock(): Int {
        return scope.calcOnState { lastBlock }
    }

    private fun setLastBlock(block: Int) {
        scope.doWithState { lastBlock = block }
    }

    private fun readVar(buf: ByteBuffer): UExpr<USort> {
        val kind = VarKind.valueOf(buf.get())
        val type = Type.valueOf(buf.get())
        val sort = ctx.typeToSort(type)
        val expr = when (kind) {
            VarKind.CONST -> readConst(buf, type)
            VarKind.PARAMETER, VarKind.LOCAL -> scope.calcOnState {
                memory.read(URegisterStackLValue(sort, resolveIndex(kind, buf.int)))
            }

            else -> throw UnknownVarKindException()
        }
        return expr.asExpr(sort)
    }

    private fun readConst(buf: ByteBuffer, type: Type): UExpr<out USort> = when (type) {
        Type.BOOL -> ctx.mkBool(buf.get() == 1.toByte())
        Type.INT8, Type.UINT8 -> ctx.mkBv(buf.get(), ctx.bv8Sort)
        Type.INT16, Type.UINT16 -> ctx.mkBv(buf.short, ctx.bv16Sort)
        Type.INT32, Type.UINT32 -> ctx.mkBv(buf.int, ctx.bv32Sort)
        Type.INT64, Type.UINT64 -> ctx.mkBv(buf.long, ctx.bv64Sort)
        Type.FLOAT32 -> ctx.mkFp(buf.float, ctx.fp32Sort)
        Type.FLOAT64 -> ctx.mkFp(buf.double, ctx.fp64Sort)
        else -> throw UnknownTypeException()
    }

    private fun readMethodInfo(buf: ByteBuffer): GoMethodInfo {
        val returnType = buf.get()
        val variablesCount = buf.int
        val parametersCount = buf.int
        val parametersTypes = Array(parametersCount) { Type.valueOf(buf.get()) }

        return GoMethodInfo(
            Type.valueOf(returnType),
            variablesCount,
            parametersCount,
            parametersTypes
        )
    }

    private fun readArrayReading(buf: ByteBuffer): ArrayReading? {
        val varIndex = resolveIndex(VarKind.LOCAL, buf.int)
        val arrayType = Type.ARRAY
        val valueType = Type.valueOf(buf.get())
        val valueSort = ctx.typeToSort(valueType)

        val array = readVar(buf).asExpr(ctx.addressSort)
        val index = readVar(buf).asExpr(ctx.sizeSort)
        val length = scope.calcOnState { memory.readArrayLength(array, arrayType, ctx.sizeSort) }

        checkNotNull(array) ?: return null
        checkIndex(index, length) ?: return null

        return ArrayReading(array, valueType, valueSort, varIndex, index)
    }

    private fun readFieldReading(buf: ByteBuffer): FieldReading? {
        val varIndex = resolveIndex(VarKind.LOCAL, buf.int)
        val valueType = Type.valueOf(buf.get())
        val valueSort = ctx.typeToSort(valueType)

        val obj = readVar(buf).let {
            if (it.sort == ctx.pointerSort) {
                deref(it, ctx.addressSort)
            } else {
                it.asExpr(ctx.addressSort)
            }
        }
        val field = buf.int

        checkNotNull(obj) ?: return null

        return FieldReading(obj, valueType, valueSort, varIndex, field)
    }

    private fun resolveIndex(kind: VarKind, value: Int): Int = when (kind) {
        VarKind.LOCAL -> value + ctx.getArgsCount(scope.calcOnState { lastEnteredMethod })
        VarKind.PARAMETER -> value
        else -> -1
    }

    private fun bv(expr: UExpr<USort>): UExpr<UBvSort> {
        return expr.asExpr(expr.sort as UBvSort)
    }

    private fun <Sort : USort> deref(expr: UExpr<USort>, sort: Sort): UExpr<Sort> {
        return scope.calcOnState {
            expr as UPointer
            memory.read(GoPointerLValue(ctx.mkConcreteHeapRef(expr.address), sort))
        }
    }

    private fun checkNotNull(obj: UHeapRef): Unit? = with(ctx) {
        scope.fork(mkHeapRefEq(obj, nullRef).not(), blockOnFalseState = {
            methodResult = GoMethodResult.Panic
        })
    }

    private fun checkIndex(index: UExpr<USizeSort>, length: UExpr<USizeSort>): Unit? = with(ctx) {
        scope.fork(mkBvSignedLessExpr(index, length), blockOnFalseState = {
            methodResult = GoMethodResult.Panic
        })
    }
}

private enum class Method(val value: Byte) {
    UNKNOWN(0),
    MK_UN_OP(1),
    MK_BIN_OP(2),
    MK_CALL(3),
    MK_CALL_BUILTIN(4),
    MK_STORE(5),
    MK_IF(6),
    MK_ALLOC(7),
    MK_RETURN(8),
    MK_VARIABLE(9),
    MK_POINTER_FIELD_READING(10),
    MK_FIELD_READING(11),
    MK_POINTER_ARRAY_READING(12),
    MK_ARRAY_READING(13),
    MK_MAP_LOOKUP(14),
    MK_MAP_UPDATE(15);

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

private enum class BuiltinFunction(val value: Byte) {
    UNKNOWN(0),
    LEN(1);

    companion object {
        private val values = values()

        fun valueOf(value: Byte) = values.firstOrNull { it.value == value } ?: throw UnknownFunctionException()
    }
}

private data class ArrayReading(
    val ref: UHeapRef,
    val type: Type,
    val sort: USort,
    val varIndex: Int,
    val index: UExpr<USizeSort>,
)

private data class FieldReading(
    val ref: UHeapRef,
    val type: Type,
    val sort: USort,
    val varIndex: Int,
    val field: Int,
)