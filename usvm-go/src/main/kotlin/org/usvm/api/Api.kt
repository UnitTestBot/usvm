package org.usvm.api

import io.ksmt.expr.KBitVec64Value
import io.ksmt.utils.asExpr
import org.usvm.UAddressPointer
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.ULValuePointer
import org.usvm.USort
import org.usvm.api.collection.ObjectMapCollectionApi.mkSymbolicObjectMap
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapAnyKey
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapGet
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapMergeInto
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapRemove
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapSize
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapGet
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapAnyKey
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapMergeInto
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapRemove
import org.usvm.bridge.GoBridge
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.machine.GoContext
import org.usvm.machine.GoInst
import org.usvm.machine.USizeSort
import org.usvm.machine.interpreter.GoStepScope
import org.usvm.machine.operator.GoBinaryOperator
import org.usvm.machine.operator.GoUnaryOperator
import org.usvm.machine.operator.mkNarrow
import org.usvm.machine.state.GoMethodResult
import org.usvm.machine.state.GoState
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
import org.usvm.types.first
import org.usvm.util.bool
import org.usvm.util.byte
import java.nio.ByteBuffer

class Api(
    private val ctx: GoContext,
    private val bridge: GoBridge,
    private val scope: GoStepScope,
) {
    fun mk(buf: ByteBuffer, inst: GoInst): GoInst {
        val lastBlock = getLastBlock()
        setLastBlock(buf.int)

        var nextInst = inst
        val method = Method.valueOf(buf.byte)
        when (method) {
            Method.MK_UN_OP -> mkUnOp(buf)
            Method.MK_BIN_OP -> mkBinOp(buf)
            Method.MK_CALL -> mkCall(buf, lastBlock).let { if (it) nextInst = 0L }
            Method.MK_CALL_BUILTIN -> mkCallBuiltin(buf, inst)
            Method.MK_CHANGE_INTERFACE -> mkChangeInterface(buf)
            Method.MK_CHANGE_TYPE -> mkChangeType(buf)
            Method.MK_CONVERT -> mkConvert(buf)
            Method.MK_MAKE_INTERFACE -> mkMakeInterface(buf)
            Method.MK_STORE -> mkStore(buf)
            Method.MK_IF -> mkIf(buf)
            Method.MK_ALLOC -> mkAlloc(buf)
            Method.MK_MAKE_SLICE -> mkMakeSlice(buf)
            Method.MK_MAKE_MAP -> mkMakeMap(buf)
            Method.MK_EXTRACT -> mkExtract(buf)
            Method.MK_RETURN -> mkReturn(buf)
            Method.MK_PANIC -> mkPanic(buf)
            Method.MK_VARIABLE -> mkVariable(buf)
            Method.MK_RANGE -> mkRange(buf)
            Method.MK_NEXT -> mkNext(buf, nextInst)
            Method.MK_POINTER_FIELD_READING -> mkPointerFieldReading(buf)
            Method.MK_FIELD_READING -> mkFieldReading(buf)
            Method.MK_POINTER_ARRAY_READING -> mkPointerArrayReading(buf)
            Method.MK_ARRAY_READING -> mkArrayReading(buf)
            Method.MK_MAP_LOOKUP -> mkMapLookup(buf, nextInst)
            Method.MK_MAP_UPDATE -> mkMapUpdate(buf)
            Method.UNKNOWN -> {}
        }

        return nextInst
    }

    private fun mkUnOp(buf: ByteBuffer) {
        val z = readVar(buf)
        val op = UnOp.valueOf(buf.byte)
        val x = readVar(buf).expr

        val expr = when (op) {
            UnOp.RECV -> TODO()
            UnOp.NEG, UnOp.NOT, UnOp.INV -> GoUnaryOperator.Neg(x)
            UnOp.DEREF -> deref(x, z.sort)
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
        val op = BinOp.valueOf(buf.byte)
        val y = readVar(buf).expr

        val signed = z.goSort.isSigned()
        val expr = when (op) {
            BinOp.ADD -> GoBinaryOperator.Add
            BinOp.SUB -> GoBinaryOperator.Sub
            BinOp.MUL -> GoBinaryOperator.Mul
            BinOp.DIV -> GoBinaryOperator.Div(signed)
            BinOp.REM -> GoBinaryOperator.Rem(signed)
            BinOp.AND -> GoBinaryOperator.And
            BinOp.OR -> GoBinaryOperator.Or
            BinOp.XOR -> GoBinaryOperator.Xor
            BinOp.SHL -> GoBinaryOperator.Shl
            BinOp.SHR -> GoBinaryOperator.Shr(signed)
            BinOp.AND_NOT -> GoBinaryOperator.AndNot
            BinOp.EQ -> GoBinaryOperator.Eq
            BinOp.LT -> GoBinaryOperator.Lt(signed)
            BinOp.GT -> GoBinaryOperator.Gt(signed)
            BinOp.NEQ -> GoBinaryOperator.Neq
            BinOp.LE -> GoBinaryOperator.Le(signed)
            BinOp.GE -> GoBinaryOperator.Ge(signed)
            else -> throw UnknownBinaryOperationException()
        }(x, normalize(y, op, signed))

        val lvalue = URegisterStackLValue(z.sort, z.index)
        return scope.doWithState {
            memory.write(lvalue, expr.asExpr(z.sort), ctx.trueExpr)
        }
    }

    private fun mkCall(buf: ByteBuffer, lastBlock: Int): Boolean {
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

        val parameters = Array<UExpr<out USort>>(buf.int) {
            readVar(buf).expr
        }

        val isInvoke = buf.bool
        val method = if (isInvoke) {
            val type = scope.calcOnState {
                memory.types.getTypeStream(parameters[0].asExpr(ctx.addressSort)).first()
            }
            bridge.methodImplementation(buf.long, type)
        } else buf.long
        val entrypoint = if (isInvoke) bridge.entryPoints(method).first[0] else buf.long

        buf.rewind()
        val methodInfo = bridge.methodInfo(method)
        ctx.setMethodInfo(method, methodInfo)

        setLastBlock(lastBlock)

        scope.doWithState {
            callStack.push(method, currentStatement)
            memory.stack.push(parameters, methodInfo.variablesCount)
            newInst(entrypoint)
        }

        return true
    }

    private fun mkCallBuiltin(buf: ByteBuffer, inst: GoInst) = with(ctx) {
        val idx = resolveIndex(VarKind.LOCAL, buf.int)
        val builtin = BuiltinFunction.valueOf(buf.byte)
        when (builtin) {
            BuiltinFunction.LEN, BuiltinFunction.CAP -> {
                val arg = readVar(buf)
                val collection = scope.calcOnState {
                    memory.read(URegisterStackLValue(addressSort, arg.index)).asExpr(addressSort)
                }
                val lvalue = URegisterStackLValue(sizeSort, idx)

                scope.fork(
                    mkHeapRefEq(collection, nullRef).not(),
                    blockOnTrueState = {
                        val rvalue = when (arg.goSort) {
                            GoSort.SLICE, GoSort.ARRAY, GoSort.STRING -> {
                                memory.readArrayLength(collection, arg.type, sizeSort)
                            }

                            GoSort.MAP -> symbolicObjectMapSize(collection, arg.type)
                            else -> throw IllegalStateException()
                        }

                        memory.write(lvalue, rvalue.asExpr(sizeSort), ctx.trueExpr)
                    },
                    blockOnFalseState = {
                        memory.write(lvalue, mkBv(0).asExpr(sizeSort), ctx.trueExpr)
                        newInst(inst)
                    }
                )
            }

            else -> throw UnknownFunctionException()
        }
    }

    private fun mkChangeInterface(buf: ByteBuffer) {
        copyVar(buf)
    }

    private fun mkChangeType(buf: ByteBuffer) {
        copyVarWithTypeConstraint(buf)
    }

    private fun mkConvert(buf: ByteBuffer) {
        val l = readVar(buf)
        val r = readVar(buf)
        val rvalue = ctx.mkPrimitiveCast(r.expr, l.sort).asExpr(l.sort)
        val lvalue = URegisterStackLValue(l.sort, l.index)

        scope.doWithState {
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun mkMakeInterface(buf: ByteBuffer) {
        copyVarWithTypeConstraint(buf)
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
        val arrayType = buf.long
        val idx = resolveIndex(kind, buf.int)
        val len = readVar(buf).expr.asExpr(ctx.sizeSort)
        readVar(buf)

        checkLength(len) ?: throw IllegalStateException()

        val lvalue = URegisterStackLValue(ctx.addressSort, idx)
        scope.doWithState {
            val ref = memory.allocConcrete(arrayType)
            memory.writeArrayLength(ref, len, arrayType, ctx.sizeSort)
            memory.write(lvalue, ref.asExpr(ctx.addressSort), ctx.trueExpr)
        }
    }

    private fun mkMakeMap(buf: ByteBuffer) {
        val kind = VarKind.LOCAL
        val mapType = buf.long
        val idx = resolveIndex(kind, buf.int)
        val reserve = readVar(buf).expr.asExpr(ctx.sizeSort)

        checkLength(reserve) ?: throw IllegalStateException()

        val lvalue = URegisterStackLValue(ctx.addressSort, idx)
        scope.doWithState {
            val ref = memory.allocConcrete(mapType)
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
        val len = buf.int
        scope.doWithState {
            when (len) {
                0, 1 -> returnValue(readVar(buf).expr)
                else -> {
                    val vars = Array(len) { readVar(buf).expr }
                    returnValue(mkTuple(this, ctx.getReturnType(lastEnteredMethod), *vars).asExpr(ctx.addressSort))
                }
            }
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

    private fun mkRange(buf: ByteBuffer) = with(ctx) {
        val iter = readVar(buf)
        val collectionVar = readVar(buf)

        val isString = collectionVar.goSort == GoSort.STRING
        val lvalue = URegisterStackLValue(iter.sort, iter.index)
        scope.doWithState {
            val (collection, key) = if (isString) {
                collectionVar.expr.asExpr(addressSort) to mkSizeExpr(0)
            } else {
                val keySort = mapSort(GoSort.valueOf(buf.byte))
                val valueSort = mapSort(GoSort.valueOf(buf.byte))

                val isRefSet = keySort == addressSort
                val mapType = collectionVar.type
                val map = copyMap(collectionVar, keySort, valueSort)

                val key = if (isRefSet) {
                    symbolicObjectMapAnyKey(map, mapType)
                } else {
                    symbolicPrimitiveMapAnyKey(map, mapType, keySort, USizeExprKeyInfo())
                }
                map to key
            }
            val rvalue = mkTuple(this, iter.type, ctx.mkBv(collectionVar.type), collection, key)
            memory.write(lvalue, rvalue.asExpr(iter.sort), ctx.trueExpr)
        }
    }

    private fun mkNext(buf: ByteBuffer, inst: GoInst) = with(ctx) {
        val tuple = readVar(buf)
        val iter = readVar(buf).expr.asExpr(addressSort)
        val isString = buf.bool

        val lvalue = URegisterStackLValue(tuple.sort, tuple.index)
        scope.doWithState {
            val type = (memory.readField(iter, 0, bv64Sort) as KBitVec64Value).longValue
            val collection = memory.readField(iter, 1, addressSort)
            checkNotNull(collection) ?: throw IllegalStateException()

            val (ok, key, value) = getCurrentKeyValue(this, buf, isString, iter, collection, type)
            scope.fork(
                ok,
                blockOnTrueState = {
                    val nextKey = computeNextKey(this, isString, key, collection, type)
                    val validTuple = mkTuple(this, tuple.type, ok, key, value).asExpr(tuple.sort)
                    memory.write(lvalue, validTuple, trueExpr)
                    memory.writeField(iter, 2, key.sort, nextKey.asExpr(key.sort), trueExpr)
                },
                blockOnFalseState = {
                    val invalidTuple = mkTuple(this, tuple.type, ok, nullRef, nullRef).asExpr(tuple.sort)
                    memory.write(lvalue, invalidTuple, trueExpr)
                    newInst(inst)
                }
            )
        }
    }

    private fun getCurrentKeyValue(
        state: GoState,
        buf: ByteBuffer,
        isString: Boolean,
        iter: UHeapRef,
        collection: UHeapRef,
        type: GoType,
    ): Triple<UExpr<UBoolSort>, UExpr<out USort>, UExpr<out USort>> = with(ctx) {
        if (isString) {
            val index = state.memory.readField(iter, 2, sizeSort)
            val value = state.memory.readArrayIndex(collection, index, type, bv32Sort)
            val length = state.memory.readArrayLength(collection, type, sizeSort)
            val ok = mkBvSignedLessExpr(index, length)
            return Triple(ok, index, value)
        }

        val keySort = mapSort(GoSort.valueOf(buf.byte))
        val valueSort = mapSort(GoSort.valueOf(buf.byte))

        val key = state.memory.readField(iter, 2, keySort)
        val value = if (keySort == addressSort) {
            state.symbolicObjectMapGet(collection, key.asExpr(addressSort), type, valueSort)
        } else {
            state.symbolicPrimitiveMapGet(collection, key.asExpr(valueSort), type, valueSort, USizeExprKeyInfo())
        }
        val length = state.symbolicObjectMapSize(collection, type)
        val ok = mkBvSignedGreaterExpr(length, mkBv(0))
        return Triple(ok, key, value)
    }

    private fun computeNextKey(
        state: GoState,
        isString: Boolean,
        oldKey: UExpr<out USort>,
        collection: UHeapRef,
        type: GoType,
    ): UExpr<out USort> = with(ctx) {
        if (isString) {
            return mkBvAddExpr(oldKey.asExpr(sizeSort), mkBv(1))
        }

        val keySort = oldKey.sort
        if (keySort == addressSort) {
            state.symbolicObjectMapRemove(collection, oldKey.asExpr(addressSort), type)
            return state.symbolicObjectMapAnyKey(collection, type)
        }

        state.symbolicPrimitiveMapRemove(collection, oldKey, type, USizeExprKeyInfo())
        return state.symbolicPrimitiveMapAnyKey(collection, type, keySort, USizeExprKeyInfo())
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
        val mapType = mapVar.type
        val map = mapVar.expr.asExpr(ctx.addressSort)
        checkNotNull(map) ?: throw IllegalStateException()

        val key = readVar(buf).expr
        val isRefKey = key.sort == ctx.addressSort

        val mapValueSort = ctx.mapSort(GoSort.valueOf(buf.byte))
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
                        mkTuple(this, value.type, it, ctx.trueExpr)
                    } else {
                        it
                    }
                }
                memory.write(lvalue, rvalue.asExpr(value.sort), ctx.trueExpr)
            },
            blockOnFalseState = {
                val rvalue = value.sort.sampleUValue().let {
                    if (commaOk) {
                        mkTuple(this, value.type, it, ctx.falseExpr)
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
        val mapType = mapVar.type
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

    private fun mkTuple(state: GoState, type: GoType, vararg fields: UExpr<out USort>): UHeapRef {
        val ref = state.memory.allocConcrete(type)
        for ((index, field) in fields.withIndex()) {
            state.memory.write(UFieldLValue(field.sort, ref, index), field.asExpr(field.sort), ctx.trueExpr)
        }
        return ref
    }

    fun getLastBlock(): Int {
        return scope.calcOnState { lastBlock }
    }

    private fun setLastBlock(block: Int) {
        scope.doWithState { lastBlock = block }
    }

    private fun copyVar(buf: ByteBuffer) {
        val l = readVar(buf)
        val rvalue = readVar(buf).expr
        val lvalue = URegisterStackLValue(l.sort, l.index)

        scope.doWithState {
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun copyVarWithTypeConstraint(buf: ByteBuffer) {
        val l = readVar(buf)
        val rvalue = readVar(buf).expr
        val lvalue = URegisterStackLValue(l.sort, l.index)

        scope.doWithState {
            if (rvalue.sort == ctx.addressSort) {
                scope.assert(memory.types.evalIsSubtype(rvalue.asExpr(ctx.addressSort), l.type))
                    ?: throw IllegalStateException()
            }
            memory.write(lvalue, rvalue, ctx.trueExpr)
        }
    }

    private fun copyMap(
        mapVar: Var,
        keySort: USort,
        valueSort: USort,
    ): UHeapRef = with(ctx) {
        val srcMap = mapVar.expr.asExpr(addressSort)
        checkNotNull(srcMap) ?: throw IllegalStateException()

        val mapType = mapVar.type
        val isRefSet = keySort == addressSort

        return scope.calcOnState {
            val destMap = mkSymbolicObjectMap(mapType)

            if (isRefSet) {
                symbolicObjectMapMergeInto(destMap, srcMap, mapType, valueSort)
            } else {
                symbolicPrimitiveMapMergeInto(destMap, srcMap, mapType, keySort, valueSort, USizeExprKeyInfo())
            }

            destMap
        }
    }

    private fun readVar(buf: ByteBuffer): Var {
        val kind = VarKind.valueOf(buf.byte)
        val type = buf.long
        val goSort = GoSort.valueOf(buf.byte)
        val sort = ctx.mapSort(goSort)
        var index = 0
        val expr = when (kind) {
            VarKind.CONST -> readConst(buf, type, sort)
            VarKind.PARAMETER, VarKind.LOCAL -> scope.calcOnState {
                index = resolveIndex(kind, buf.int)
                memory.read(URegisterStackLValue(sort, index))
            }

            else -> throw UnknownVarKindException()
        }
        return Var(expr.asExpr(sort), type, goSort, sort, index)
    }

    private fun readConst(buf: ByteBuffer, type: GoType, sort: USort): UExpr<out USort> = with(ctx) {
        when (sort) {
            voidSort -> voidValue
            boolSort -> mkBool(buf.bool)
            bv8Sort -> mkBv(buf.byte, bv8Sort)
            bv16Sort -> mkBv(buf.short, bv16Sort)
            bv32Sort -> mkBv(buf.int, bv32Sort)
            bv64Sort -> mkBv(buf.long, bv64Sort)
            fp32Sort -> mkFp(buf.float, fp32Sort)
            fp64Sort -> mkFp(buf.double, fp64Sort)
            stringSort -> scope.calcOnState {
                val len = buf.int
                val content = Array(len) { mkBv(buf.int) }.asSequence()
                memory.allocateArrayInitialized(type, bv32Sort, sizeSort, content)
            }

            else -> throw UnknownTypeException()
        }
    }

    private fun readArrayReading(buf: ByteBuffer): ArrayReading? {
        val varIndex = resolveIndex(VarKind.LOCAL, buf.int)
        val arrayType = buf.long
        val valueType = buf.long
        val valueSort = ctx.mapSort(GoSort.valueOf(buf.byte))

        val array = readVar(buf).expr.asExpr(ctx.addressSort)
        val indexVar = readVar(buf)
        val index = ctx.mkNarrow(bv(indexVar.expr), Int.SIZE_BITS, indexVar.goSort.isSigned()).asExpr(ctx.sizeSort)
        val length = scope.calcOnState { memory.readArrayLength(array, arrayType, ctx.sizeSort) }

        checkNotNull(array) ?: return null
        checkIndex(index, length) ?: return null

        return ArrayReading(array, arrayType, valueType, valueSort, varIndex, index)
    }

    private fun readFieldReading(buf: ByteBuffer): FieldReading? {
        val index = resolveIndex(VarKind.LOCAL, buf.int)
        val type = buf.long
        val sort = ctx.mapSort(GoSort.valueOf(buf.byte))

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

    private fun normalize(expr: UExpr<USort>, op: BinOp, signed: Boolean): UExpr<USort> = when (op) {
        BinOp.SHR -> bv(expr).mkNarrow(Long.SIZE_BITS, signed).asExpr(ctx.bv64Sort)
        else -> expr
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
    MK_CHANGE_INTERFACE(5),
    MK_CHANGE_TYPE(6),
    MK_CONVERT(7),
    MK_MAKE_INTERFACE(8),
    MK_STORE(9),
    MK_IF(10),
    MK_ALLOC(11),
    MK_MAKE_SLICE(12),
    MK_MAKE_MAP(13),
    MK_EXTRACT(14),
    MK_RETURN(15),
    MK_PANIC(16),
    MK_VARIABLE(17),
    MK_RANGE(18),
    MK_NEXT(19),
    MK_POINTER_FIELD_READING(20),
    MK_FIELD_READING(21),
    MK_POINTER_ARRAY_READING(22),
    MK_ARRAY_READING(23),
    MK_MAP_LOOKUP(24),
    MK_MAP_UPDATE(25);

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
    REM(5),
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
    val goSort: GoSort,
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