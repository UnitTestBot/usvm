package org.usvm.api

import io.ksmt.utils.asExpr
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UAddressPointer
import org.usvm.ULValuePointer
import org.usvm.USort
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapSize
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.machine.GoContext
import org.usvm.machine.GoInst
import org.usvm.machine.GoMethodInfo
import org.usvm.machine.USizeSort
import org.usvm.machine.interpreter.GoStepScope
import org.usvm.machine.state.GoMethodResult
import org.usvm.machine.type.GoSort
import org.usvm.machine.type.GoType
import org.usvm.memory.GoPointerLValue
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.sampleUValue
import org.usvm.sizeSort
import org.usvm.util.bool
import java.nio.ByteBuffer

class Api(
    private val ctx: GoContext,
    private val scope: GoStepScope,
) {
    fun mk(buf: ByteBuffer, inst: GoInst): GoInst {
        var nextInst = inst
        val method = Method.valueOf(buf.get())
        when (method) {
            Method.MK_UN_OP -> mkUnOp(buf)
            Method.MK_BIN_OP -> mkBinOp(buf)
            Method.MK_CALL -> mkCall(buf).let { if (it) nextInst = 0L }
            Method.MK_CALL_BUILTIN -> mkCallBuiltin(buf)
            Method.MK_STORE -> mkStore(buf)
            Method.MK_IF -> mkIf(buf)
            Method.MK_ALLOC -> mkAlloc(buf)
            Method.MK_MAKE_SLICE -> mkMakeSlice(buf)
            Method.MK_MAKE_MAP -> mkMakeMap(buf)
            Method.MK_EXTRACT -> mkExtract(buf)
            Method.MK_RETURN -> mkReturn(buf)
            Method.MK_PANIC -> mkPanic(buf)
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
        val z = readVar(buf)
        val op = UnOp.valueOf(buf.get())
        val x = readVar(buf).expr

        val expr = when (op) {
            UnOp.RECV -> TODO()
            UnOp.NEG -> ctx.mkBvNegationExpr(bv(x))
            UnOp.DEREF -> deref(x, z.sort)
            UnOp.NOT -> ctx.mkNot(x.asExpr(ctx.boolSort))
            UnOp.INV -> ctx.mkBvNotExpr(bv(x))
            else -> throw UnknownUnaryOperationException()
        }

        val lvalue = URegisterStackLValue(z.sort, z.index)
        scope.doWithState {
            memory.write(lvalue, expr.asExpr(z.sort), ctx.trueExpr)
        }
    }

    private fun mkBinOp(buf: ByteBuffer) {
        val z = readVar(buf)
        val x = readVar(buf).expr
        val op = BinOp.valueOf(buf.get())
        val y = readVar(buf).expr

        val signed = z.isSigned
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

        val lvalue = URegisterStackLValue(z.sort, z.index)
        scope.doWithState {
            memory.write(lvalue, expr.asExpr(z.sort), ctx.trueExpr)
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
            readVar(buf).expr
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
            BuiltinFunction.LEN, BuiltinFunction.CAP -> {
                val arrayType = buf.long
                val arg = readVar(buf)
                scope.calcOnState {
                    val array = memory.read(URegisterStackLValue(ctx.addressSort, arg.index))
                    memory.readArrayLength(array.asExpr(ctx.addressSort), arrayType, ctx.sizeSort)
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
        val pointer = readVar(buf).expr as UAddressPointer
        val rvalue = readVar(buf).expr
        val lvalue = pointerLValue(pointer, rvalue.sort)
        scope.doWithState {
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun mkIf(buf: ByteBuffer) {
        val expr = readVar(buf).expr
        val pos = buf.long
        val neg = buf.long
        scope.forkWithBlackList(
            expr.asExpr(ctx.boolSort),
            pos,
            neg,
            blockOnTrueState = { newInst(pos) },
            blockOnFalseState = { newInst(neg) }
        )
    }

    private fun mkAlloc(buf: ByteBuffer) {
        val kind = VarKind.LOCAL
        val type = buf.long
        val idx = resolveIndex(kind, buf.int)

        val lvalue = URegisterStackLValue(ctx.pointerSort, idx)
        scope.doWithState {
            val ref = memory.allocConcrete(type)
            memory.write(lvalue, ctx.mkAddressPointer(ref.address), ctx.trueExpr)
        }
    }

    private fun mkMakeSlice(buf: ByteBuffer) {
        val kind = VarKind.LOCAL
        val refType = buf.long
        val arrayType = buf.long
        val idx = resolveIndex(kind, buf.int)
        val len = readVar(buf).expr.asExpr(ctx.sizeSort)
        readVar(buf)

        checkLength(len) ?: throw IllegalStateException()

        val lvalue = URegisterStackLValue(ctx.addressSort, idx)
        scope.doWithState {
            val ref = memory.allocConcrete(refType)
            memory.writeArrayLength(ref, len, arrayType, ctx.sizeSort)
            memory.write(lvalue, ref.asExpr(ctx.addressSort), ctx.trueExpr)
        }
    }

    private fun mkMakeMap(buf: ByteBuffer) {
        val kind = VarKind.LOCAL
        val refType = buf.long
        val mapType = buf.long
        val idx = resolveIndex(kind, buf.int)
        val reserve = readVar(buf).expr.asExpr(ctx.sizeSort)

        checkLength(reserve) ?: throw IllegalStateException()

        val lvalue = URegisterStackLValue(ctx.addressSort, idx)
        scope.doWithState {
            val ref = memory.allocConcrete(refType)
            memory.write(UMapLengthLValue(ref, mapType, ctx.sizeSort), reserve, ctx.trueExpr)
            memory.write(lvalue, ref.asExpr(ctx.addressSort), ctx.trueExpr)
        }
    }

    private fun mkExtract(buf: ByteBuffer) {
        val element = readVar(buf)
        val tuple = readVar(buf)
        val index = buf.int

        scope.doWithState {
            val rvalue = memory.readField(tuple.expr.asExpr(ctx.addressSort), index, element.sort)
            memory.write(URegisterStackLValue(element.sort, element.index), rvalue, ctx.trueExpr)
        }
    }

    private fun mkReturn(buf: ByteBuffer) {
        scope.doWithState {
            returnValue(readVar(buf).expr)
        }
    }

    private fun mkPanic(buf: ByteBuffer) {
        val expr = readVar(buf)
        scope.doWithState {
            methodResult = GoMethodResult.Panic(expr)
        }
    }

    private fun mkVariable(buf: ByteBuffer) {
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val rvalue = readVar(buf).expr
        val lvalue = URegisterStackLValue(rvalue.sort, idx)
        scope.doWithState {
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun mkPointerFieldReading(buf: ByteBuffer) {
        val obj = readFieldReading(buf) ?: throw IllegalStateException()
        val lvalue = URegisterStackLValue(ctx.pointerSort, obj.register)
        scope.doWithState {
            val ref = memory.allocConcrete(obj.type)
            val field = UFieldLValue(obj.sort, obj.ref, obj.field)
            memory.write(GoPointerLValue(ref, obj.sort), ctx.mkLValuePointer(field), ctx.trueExpr)
            memory.write(lvalue, ctx.mkAddressPointer(ref.address), ctx.trueExpr)
        }
    }

    private fun mkFieldReading(buf: ByteBuffer) {
        val obj = readFieldReading(buf) ?: throw IllegalStateException()
        val lvalue = URegisterStackLValue(obj.sort, obj.register)
        scope.doWithState {
            val rvalue = memory.readField(obj.ref, obj.field, obj.sort)
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun mkPointerArrayReading(buf: ByteBuffer) {
        val arr = readArrayReading(buf) ?: throw IllegalStateException()
        val lvalue = URegisterStackLValue(ctx.pointerSort, arr.register)
        scope.doWithState {
            val ref = memory.allocConcrete(arr.type)
            val element = UArrayIndexLValue(arr.sort, arr.ref, arr.index, arr.arrayType)
            memory.write(GoPointerLValue(ref, arr.sort), ctx.mkLValuePointer(element), ctx.trueExpr)
            memory.write(lvalue, ctx.mkAddressPointer(ref.address), ctx.trueExpr)
        }
    }

    private fun mkArrayReading(buf: ByteBuffer) {
        val arr = readArrayReading(buf) ?: throw IllegalStateException()
        val lvalue = URegisterStackLValue(arr.sort, arr.register)
        scope.doWithState {
            val rvalue = memory.readArrayIndex(arr.ref, arr.index, arr.arrayType, arr.sort)
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun mkMapLookup(buf: ByteBuffer, nextInst: GoInst) {
        val value = readVar(buf)

        val mapVar = readVar(buf)
        val mapType = buf.long
        val map = mapVar.expr.asExpr(ctx.addressSort)
        checkNotNull(map) ?: throw IllegalStateException()

        val key = readVar(buf).expr
        val isRefKey = key.sort == ctx.addressSort

        val mapValueSort = ctx.mapSort(GoSort.valueOf(buf.get()))
        val commaOk = buf.bool

        val contains = scope.calcOnState {
            if (isRefKey) {
                memory.refSetContainsElement(map, key.asExpr(ctx.addressSort), mapType)
            } else {
                memory.setContainsElement(map, key, mapType, USizeExprKeyInfo())
            }
        }
        val lvalue = URegisterStackLValue(value.sort, value.index)
        scope.fork(
            contains,
            blockOnTrueState = {
                val entry = if (isRefKey) {
                    URefMapEntryLValue(mapValueSort, map, key.asExpr(ctx.addressSort), mapType)
                } else {
                    UMapEntryLValue(key.sort, mapValueSort, map, key, mapType, USizeExprKeyInfo())
                }
                val rvalue = memory.read(entry).let {
                    if (commaOk) {
                        mkTuple(value.type, it, ctx.trueExpr)
                    } else {
                        it
                    }
                }
                memory.write(lvalue, rvalue.asExpr(value.sort), ctx.trueExpr)
            },
            blockOnFalseState = {
                val rvalue = value.sort.sampleUValue().let {
                    if (commaOk) {
                        mkTuple(value.type, it, ctx.falseExpr)
                    } else {
                        it
                    }
                }
                memory.write(lvalue, rvalue.asExpr(value.sort), ctx.trueExpr)
                newInst(nextInst)
            }
        )
    }

    private fun mkMapUpdate(buf: ByteBuffer) = with(ctx) {
        val mapVar = readVar(buf)
        val mapType = buf.long
        val map = mapVar.expr.asExpr(ctx.addressSort)
        val key = readVar(buf).expr
        val value = readVar(buf).expr

        checkNotNull(map) ?: throw IllegalStateException()

        scope.doWithState {
            val mapContainsLValue = USetEntryLValue(key.sort, map, key, mapType, USizeExprKeyInfo())
            val currentSize = symbolicObjectMapSize(map, mapType)

            val keyIsInMap = memory.read(mapContainsLValue)
            val keyIsNew = mkNot(keyIsInMap)

            memory.write(UMapEntryLValue(key.sort, value.sort, map, key, mapType, USizeExprKeyInfo()), value, trueExpr)
            memory.write(mapContainsLValue, trueExpr, trueExpr)

            val updatedSize = mkSizeAddExpr(currentSize, mkSizeExpr(1))
            memory.write(UMapLengthLValue(map, mapType, sizeSort), updatedSize, keyIsNew)
        }
    }

    private fun mkTuple(type: GoType, vararg fields: UExpr<out USort>): UHeapRef = scope.calcOnState {
        val ref = memory.allocConcrete(type)
        for ((index, field) in fields.withIndex()) {
            memory.write(UFieldLValue(field.sort, ref, index), field.asExpr(field.sort), ctx.trueExpr)
        }
        ref
    }

    fun getLastBlock(): Int {
        return scope.calcOnState { lastBlock }
    }

    private fun setLastBlock(block: Int) {
        scope.doWithState { lastBlock = block }
    }

    private fun readVar(buf: ByteBuffer): Var {
        val kind = VarKind.valueOf(buf.get())
        val type = buf.long
        val goSort = GoSort.valueOf(buf.get())
        val sort = ctx.mapSort(goSort)
        var index = 0
        val expr = when (kind) {
            VarKind.CONST -> readConst(buf, sort)
            VarKind.PARAMETER, VarKind.LOCAL -> scope.calcOnState {
                index = resolveIndex(kind, buf.int)
                memory.read(URegisterStackLValue(sort, index))
            }

            else -> throw UnknownVarKindException()
        }
        return Var(expr.asExpr(sort), type, goSort.isSigned(), sort, index)
    }

    private fun readConst(buf: ByteBuffer, sort: USort): UExpr<out USort> = when (sort) {
        ctx.boolSort -> ctx.mkBool(buf.bool)
        ctx.bv8Sort -> ctx.mkBv(buf.get(), ctx.bv8Sort)
        ctx.bv16Sort -> ctx.mkBv(buf.short, ctx.bv16Sort)
        ctx.bv32Sort -> ctx.mkBv(buf.int, ctx.bv32Sort)
        ctx.bv64Sort -> ctx.mkBv(buf.long, ctx.bv64Sort)
        ctx.fp32Sort -> ctx.mkFp(buf.float, ctx.fp32Sort)
        ctx.fp64Sort -> ctx.mkFp(buf.double, ctx.fp64Sort)
        else -> throw UnknownTypeException()
    }

    private fun readMethodInfo(buf: ByteBuffer): GoMethodInfo {
        val returnType = buf.long
        val variablesCount = buf.int
        val parametersCount = buf.int
        val parametersTypes = Array(parametersCount) { buf.long }

        return GoMethodInfo(
            returnType,
            variablesCount,
            parametersCount,
            parametersTypes
        )
    }

    private fun readArrayReading(buf: ByteBuffer): ArrayReading? {
        val varIndex = resolveIndex(VarKind.LOCAL, buf.int)
        val arrayType = buf.long
        val valueType = buf.long
        val valueSort = ctx.mapSort(GoSort.valueOf(buf.get()))

        val array = readVar(buf).expr.asExpr(ctx.addressSort)
        val index = readVar(buf).expr.asExpr(ctx.sizeSort)
        val length = scope.calcOnState { memory.readArrayLength(array, arrayType, ctx.sizeSort) }

        checkNotNull(array) ?: return null
        checkIndex(index, length) ?: return null

        return ArrayReading(array, arrayType, valueType, valueSort, varIndex, index)
    }

    private fun readFieldReading(buf: ByteBuffer): FieldReading? {
        val index = resolveIndex(VarKind.LOCAL, buf.int)
        val type = buf.long
        val sort = ctx.mapSort(GoSort.valueOf(buf.get()))

        val obj = readVar(buf).expr.let {
            if (it.sort == ctx.pointerSort) {
                deref(it, ctx.addressSort)
            } else {
                it.asExpr(ctx.addressSort)
            }
        }
        val field = buf.int

        checkNotNull(obj) ?: return null

        return FieldReading(obj, type, sort, index, field)
    }

    private fun resolveIndex(kind: VarKind, value: Int): Int = when (kind) {
        VarKind.LOCAL -> value + ctx.getArgsCount(scope.calcOnState { lastEnteredMethod })
        VarKind.PARAMETER -> value
        else -> -1
    }

    private fun bv(expr: UExpr<USort>): UExpr<UBvSort> {
        return expr.asExpr(expr.sort as UBvSort)
    }

    private fun <Sort : USort> deref(pointer: UExpr<out USort>, sort: Sort): UExpr<Sort> {
        return scope.calcOnState {
            pointer as UAddressPointer
            memory.read(pointerLValue(pointer, sort))
        }
    }

    private fun <Sort : USort> pointerLValue(pointer: UExpr<out USort>, sort: Sort): ULValue<*, Sort> {
        return scope.calcOnState {
            pointer as UAddressPointer
            val lvalue = GoPointerLValue(ctx.mkConcreteHeapRef(pointer.address), sort)
            val ref = memory.read(lvalue)
            if (ref is ULValuePointer) {
                ref.lvalue.withSort(sort)
            } else {
                lvalue
            }
        }
    }

    private fun checkNotNull(obj: UHeapRef): Unit? = with(ctx) {
        scope.fork(mkHeapRefEq(obj, nullRef).not(), blockOnFalseState = {
            methodResult = GoMethodResult.Panic("null")
        })
    }

    private fun checkIndex(index: UExpr<USizeSort>, length: UExpr<USizeSort>): Unit? = with(ctx) {
        scope.fork(mkBvSignedLessExpr(index, length), blockOnFalseState = {
            methodResult = GoMethodResult.Panic("index out of bounds")
        })
    }

    private fun checkLength(length: UExpr<USizeSort>): Unit? = with(ctx) {
        scope.fork(mkBvSignedGreaterOrEqualExpr(length, mkBv(0)), blockOnFalseState = {
            methodResult = GoMethodResult.Panic("length < 0")
        })
    }

    private fun <T : USort> ULValue<*, *>.withSort(sort: T): ULValue<*, T> {
        check(this@withSort.sort == sort) { "Sort mismatch" }

        @Suppress("UNCHECKED_CAST")
        return this@withSort as ULValue<*, T>
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
    MK_MAKE_SLICE(8),
    MK_MAKE_MAP(9),
    MK_EXTRACT(10),
    MK_RETURN(11),
    MK_PANIC(12),
    MK_VARIABLE(13),
    MK_POINTER_FIELD_READING(14),
    MK_FIELD_READING(15),
    MK_POINTER_ARRAY_READING(16),
    MK_ARRAY_READING(17),
    MK_MAP_LOOKUP(18),
    MK_MAP_UPDATE(19);

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
    LEN(1),
    CAP(2);

    companion object {
        private val values = values()

        fun valueOf(value: Byte) = values.firstOrNull { it.value == value } ?: throw UnknownFunctionException()
    }
}

private data class Var(
    val expr: UExpr<USort>,
    val type: GoType,
    val isSigned: Boolean,
    val sort: USort,
    val index: Int,
)

private data class ArrayReading(
    val ref: UHeapRef,
    val arrayType: GoType,
    val type: GoType,
    val sort: USort,
    val register: Int,
    val index: UExpr<USizeSort>,
)

private data class FieldReading(
    val ref: UHeapRef,
    val type: GoType,
    val sort: USort,
    val register: Int,
    val field: Int,
)