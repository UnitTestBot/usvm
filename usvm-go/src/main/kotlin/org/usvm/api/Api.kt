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
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeSubExpr
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
            Method.MK_SLICE_TO_ARRAY_POINTER -> mkSliceToArrayPointer(buf)
            Method.MK_MAKE_INTERFACE -> mkMakeInterface(buf)
            Method.MK_STORE -> mkStore(buf)
            Method.MK_IF -> mkIf(buf)
            Method.MK_ALLOC -> mkAlloc(buf)
            Method.MK_MAKE_SLICE -> mkMakeSlice(buf)
            Method.MK_MAKE_MAP -> mkMakeMap(buf)
            Method.MK_EXTRACT -> mkExtract(buf)
            Method.MK_SLICE -> mkSlice(buf)
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
            Method.MK_TYPE_ASSERT -> mkTypeAssert(buf)
            Method.MK_MAKE_CLOSURE -> mkMakeClosure(buf)
            Method.UNKNOWN -> {}
        }

        return nextInst
    }

    private fun mkUnOp(buf: ByteBuffer) = with(ctx) {
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
            memory.write(lvalue, expr.asExpr(z.sort), trueExpr)
        }
    }

    private fun mkBinOp(buf: ByteBuffer) = with(ctx) {
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
        scope.doWithState {
            memory.write(lvalue, expr.asExpr(z.sort), trueExpr)
        }
    }

    private fun mkCall(buf: ByteBuffer, lastBlock: Int): Boolean = with(ctx) {
        val index = resolveIndex(VarKind.LOCAL, buf.int)
        val result = scope.calcOnState { methodResult }
        if (result is GoMethodResult.Success) {
            scope.doWithState {
                val lvalue = URegisterStackLValue(result.value.sort, index)
                memory.write(lvalue, result.value, trueExpr)
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
                memory.types.getTypeStream(parameters[0].asExpr(addressSort)).first()
            }
            bridge.methodImplementation(buf.long, type)
        } else buf.long
        val entrypoint = if (isInvoke) bridge.entryPoints(method).first[0] else buf.long

        buf.rewind()
        val methodInfo = bridge.methodInfo(method)
        setMethodInfo(method, methodInfo)

        setLastBlock(lastBlock)

        scope.doWithState {
            callStack.push(method, currentStatement)
            memory.stack.push(parameters, methodInfo.variablesCount)
            getFreeVariables(method)?.forEachIndexed { index, variable ->
                val lvalue = URegisterStackLValue(variable.sort, resolveIndex(VarKind.FREE_VARIABLE, index))
                memory.write(lvalue, variable, trueExpr)
            }
            newInst(entrypoint)
        }

        return true
    }

    private fun mkCallBuiltin(buf: ByteBuffer, inst: GoInst) = with(ctx) {
        val index = resolveIndex(VarKind.LOCAL, buf.int)
        val builtin = BuiltinFunction.valueOf(buf.byte)
        when (builtin) {
            BuiltinFunction.LEN, BuiltinFunction.CAP -> {
                val arg = readVar(buf)
                val collection = scope.calcOnState {
                    memory.read(URegisterStackLValue(addressSort, arg.index)).asExpr(addressSort)
                }
                val type = arg.underlyingType.let { getSliceType(it) ?: it }
                val lvalue = URegisterStackLValue(sizeSort, index)

                scope.fork(
                    mkHeapRefEq(collection, nullRef).not(),
                    blockOnTrueState = {
                        val rvalue = when (arg.goSort) {
                            GoSort.SLICE, GoSort.ARRAY, GoSort.STRING -> {
                                memory.readArrayLength(collection, type, sizeSort)
                            }

                            GoSort.MAP -> symbolicObjectMapSize(collection, type)
                            else -> throw IllegalStateException()
                        }

                        memory.write(lvalue, rvalue.asExpr(sizeSort), trueExpr)
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

    private fun mkConvert(buf: ByteBuffer) = with(ctx) {
        val l = readVar(buf)
        val r = readVar(buf)
        val rvalue = mkPrimitiveCast(r.expr, l.sort).asExpr(l.sort)
        val lvalue = URegisterStackLValue(l.sort, l.index)

        scope.doWithState {
            memory.write(lvalue, rvalue, trueExpr)
        }
    }

    private fun mkSliceToArrayPointer(buf: ByteBuffer) = with(ctx) {
        val index = resolveIndex(VarKind.LOCAL, buf.int)
        val arrayType = buf.long
        val arrayValueSort = mapSort(GoSort.valueOf(buf.byte))
        val arrayLength = mkSizeExpr(buf.long.toInt())

        val slice = readVar(buf)
        val sliceType = slice.underlyingType
        val sliceRef = slice.expr.asExpr(addressSort)
        val sliceLength = scope.calcOnState {
            memory.readArrayLength(sliceRef, sliceType, sizeSort)
        }

        checkNotNull(sliceRef) ?: throw IllegalStateException()
        checkSliceToArrayPointerLength(sliceLength, arrayLength) ?: throw IllegalStateException()

        setSliceType(arrayType, sliceType)

        val lvalue = URegisterStackLValue(pointerSort, index)
        scope.doWithState {
            val arrayRef = memory.allocConcrete(sliceType)
            memory.writeArrayLength(arrayRef, arrayLength, sliceType, sizeSort)
            memory.memcpy(sliceRef, arrayRef, sliceType, arrayValueSort, mkSizeExpr(0), mkSizeExpr(0), arrayLength)
            memory.write(GoPointerLValue(arrayRef, addressSort), arrayRef, trueExpr)
            memory.write(lvalue, mkAddressPointer(arrayRef.address), trueExpr)
        }
    }

    private fun mkMakeInterface(buf: ByteBuffer) {
        copyVarWithTypeConstraint(buf)
    }

    private fun mkStore(buf: ByteBuffer) = with(ctx) {
        val pointer = readVar(buf).expr as UAddressPointer
        val rvalue = readVar(buf).expr
        val lvalue = pointerLValue(pointer, rvalue.sort)
        scope.doWithState {
            memory.write(lvalue, rvalue, trueExpr)
        }
    }

    private fun mkIf(buf: ByteBuffer) = with(ctx) {
        val expr = readVar(buf).expr
        val pos = buf.long
        val neg = buf.long
        scope.forkWithBlackList(
            expr.asExpr(boolSort),
            pos,
            neg,
            blockOnTrueState = { newInst(pos) },
            blockOnFalseState = { newInst(neg) }
        )
    }

    private fun mkAlloc(buf: ByteBuffer) = with(ctx) {
        val index = resolveIndex(VarKind.LOCAL, buf.int)
        val type = buf.long

        val lvalue = URegisterStackLValue(pointerSort, index)
        scope.doWithState {
            val ref = memory.allocConcrete(type)
            memory.write(GoPointerLValue(ref, addressSort), ref, trueExpr)
            memory.write(lvalue, mkAddressPointer(ref.address), trueExpr)
        }
    }

    private fun mkMakeSlice(buf: ByteBuffer) = with(ctx) {
        val index = resolveIndex(VarKind.LOCAL, buf.int)
        val arrayType = buf.long
        val length = readVar(buf).expr.asExpr(sizeSort)

        checkLength(length) ?: throw IllegalStateException()

        val lvalue = URegisterStackLValue(addressSort, index)
        scope.doWithState {
            val ref = memory.allocConcrete(arrayType)
            memory.writeArrayLength(ref, length, arrayType, sizeSort)
            memory.write(lvalue, ref.asExpr(addressSort), trueExpr)
        }
    }

    private fun mkMakeMap(buf: ByteBuffer) = with(ctx) {
        val index = resolveIndex(VarKind.LOCAL, buf.int)
        val mapType = buf.long
        val reserve = readVar(buf).expr.asExpr(sizeSort)

        checkLength(reserve) ?: throw IllegalStateException()

        val lvalue = URegisterStackLValue(addressSort, index)
        scope.doWithState {
            val ref = memory.allocConcrete(mapType)
            memory.write(UMapLengthLValue(ref, mapType, sizeSort), reserve, trueExpr)
            memory.write(lvalue, ref.asExpr(addressSort), trueExpr)
        }
    }

    private fun mkExtract(buf: ByteBuffer) = with(ctx) {
        val element = readVar(buf)
        val tuple = readVar(buf)
        val index = buf.int

        scope.doWithState {
            val rvalue = memory.readField(tuple.expr.asExpr(addressSort), index, element.sort)
            memory.write(URegisterStackLValue(element.sort, element.index), rvalue, trueExpr)
        }
    }

    private fun mkSlice(buf: ByteBuffer) = with(ctx) {
        val target = readVar(buf)
        val targetValueSort = mapSort(GoSort.valueOf(buf.byte))
        val targetType = target.underlyingType

        val source = readVar(buf)
        val sourceType = source.underlyingType
        val sourceRef = source.expr.let {
            if (it.sort == pointerSort) {
                deref(it, addressSort)
            } else {
                it.asExpr(addressSort)
            }
        }
        val sourceLength = scope.calcOnState {
            memory.readArrayLength(sourceRef, sourceType, sizeSort)
        }

        val low = readVar(buf).expr.let {
            if (it.sort == sizeSort) {
                it.asExpr(sizeSort)
            } else {
                mkSizeExpr(0)
            }
        }
        val high = readVar(buf).expr.let {
            if (it.sort == sizeSort) {
                it.asExpr(sizeSort)
            } else {
                sourceLength
            }
        }
        val length = mkSizeSubExpr(high, low)

        checkNotNull(sourceRef) ?: throw IllegalStateException()
        checkNegativeIndex(low) ?: throw IllegalStateException()
        checkNegativeIndex(high) ?: throw IllegalStateException()
        checkIndexOutOfBounds(high, mkSizeAddExpr(sourceLength, mkSizeExpr(1))) ?: throw IllegalStateException()
        checkIndexOutOfBounds(low, mkSizeAddExpr(high, mkSizeExpr(1))) ?: throw IllegalStateException()

        val lvalue = URegisterStackLValue(addressSort, target.index)
        scope.doWithState {
            val targetRef = memory.allocConcrete(targetType)
            memory.writeArrayLength(targetRef, length, targetType, sizeSort)
            memory.memcpy(sourceRef, targetRef, targetType, targetValueSort, low, mkSizeExpr(0), length)
            memory.write(lvalue, targetRef.asExpr(addressSort), trueExpr)
        }
    }

    private fun mkReturn(buf: ByteBuffer) = with(ctx) {
        val length = buf.int
        scope.doWithState {
            when (length) {
                0, 1 -> returnValue(readVar(buf).expr)
                else -> {
                    val vars = Array(length) { readVar(buf).expr }
                    returnValue(mkTuple(this, getReturnType(lastEnteredMethod), *vars).asExpr(addressSort))
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

    private fun mkVariable(buf: ByteBuffer) = with(ctx) {
        val index = resolveIndex(VarKind.LOCAL, buf.int)
        val rvalue = readVar(buf).expr
        val lvalue = URegisterStackLValue(rvalue.sort, index)
        scope.doWithState {
            memory.write(lvalue, rvalue, trueExpr)
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
            val rvalue = mkTuple(this, iter.type, mkBv(collectionVar.type), collection, key)
            memory.write(lvalue, rvalue.asExpr(iter.sort), trueExpr)
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

            val (ok, key, value) = getIterCurrentKeyValue(this, buf, isString, iter, collection, type)
            scope.fork(
                ok,
                blockOnTrueState = {
                    val nextKey = computeIterNextKey(this, isString, key, collection, type)
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

    private fun getIterCurrentKeyValue(
        state: GoState,
        buf: ByteBuffer,
        isString: Boolean,
        iter: UHeapRef,
        collection: UHeapRef,
        type: GoType,
    ): Triple<UExpr<UBoolSort>, UExpr<out USort>, UExpr<out USort>> = with(ctx) {
        val notNull = mkHeapRefEq(collection, nullRef).not()

        if (isString) {
            val index = state.memory.readField(iter, 2, sizeSort)
            val value = state.memory.readArrayIndex(collection, index, type, bv32Sort)
            val length = state.memory.readArrayLength(collection, type, sizeSort)
            val ok = mkAnd(notNull, mkBvSignedLessExpr(index, length))
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
        val ok = mkAnd(notNull, mkBvSignedGreaterExpr(length, mkBv(0)))
        return Triple(ok, key, value)
    }

    private fun computeIterNextKey(
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

    private fun mkPointerFieldReading(buf: ByteBuffer) = with(ctx) {
        val obj = readFieldReading(buf)
        val lvalue = URegisterStackLValue(pointerSort, obj.register)
        scope.doWithState {
            val ref = memory.allocConcrete(obj.type)
            val field = UFieldLValue(obj.sort, obj.ref, obj.field)
            memory.write(GoPointerLValue(ref, obj.sort), mkLValuePointer(field), trueExpr)
            memory.write(lvalue, mkAddressPointer(ref.address), trueExpr)
        }
    }

    private fun mkFieldReading(buf: ByteBuffer) = with(ctx) {
        val obj = readFieldReading(buf)
        val lvalue = URegisterStackLValue(obj.sort, obj.register)
        scope.doWithState {
            val rvalue = memory.readField(obj.ref, obj.field, obj.sort)
            memory.write(lvalue, rvalue, trueExpr)
        }
    }

    private fun mkPointerArrayReading(buf: ByteBuffer) = with(ctx) {
        val arr = readArrayReading(buf)
        val lvalue = URegisterStackLValue(pointerSort, arr.register)
        scope.doWithState {
            val ref = memory.allocConcrete(arr.type)
            val element = UArrayIndexLValue(arr.sort, arr.ref, arr.index, arr.arrayType)
            memory.write(GoPointerLValue(ref, arr.sort), mkLValuePointer(element), trueExpr)
            memory.write(lvalue, mkAddressPointer(ref.address), trueExpr)
        }
    }

    private fun mkArrayReading(buf: ByteBuffer) = with(ctx) {
        val arr = readArrayReading(buf)
        val lvalue = URegisterStackLValue(arr.sort, arr.register)
        scope.doWithState {
            val rvalue = memory.readArrayIndex(arr.ref, arr.index, arr.arrayType, arr.sort)
            memory.write(lvalue, rvalue, trueExpr)
        }
    }

    private fun mkMapLookup(buf: ByteBuffer, nextInst: GoInst) = with(ctx) {
        val value = readVar(buf)

        val mapVar = readVar(buf)
        val mapType = mapVar.underlyingType
        val map = mapVar.expr.asExpr(addressSort)
        checkNotNull(map) ?: throw IllegalStateException()

        val key = readVar(buf).expr
        val isRefKey = key.sort == addressSort

        val mapValueSort = mapSort(GoSort.valueOf(buf.byte))
        val commaOk = buf.bool

        val contains = scope.calcOnState {
            if (isRefKey) {
                memory.refSetContainsElement(map, key.asExpr(addressSort), mapType)
            } else {
                memory.setContainsElement(map, key, mapType, USizeExprKeyInfo())
            }
        }
        val lvalue = URegisterStackLValue(value.sort, value.index)
        scope.fork(
            contains,
            blockOnTrueState = {
                val entry = if (isRefKey) {
                    URefMapEntryLValue(mapValueSort, map, key.asExpr(addressSort), mapType)
                } else {
                    UMapEntryLValue(key.sort, mapValueSort, map, key, mapType, USizeExprKeyInfo())
                }
                val rvalue = memory.read(entry).let {
                    if (commaOk) {
                        mkTuple(this, value.type, it, trueExpr)
                    } else {
                        it
                    }
                }
                memory.write(lvalue, rvalue.asExpr(value.sort), trueExpr)
            },
            blockOnFalseState = {
                val rvalue = value.sort.sampleUValue().let {
                    if (commaOk) {
                        mkTuple(this, value.type, it, falseExpr)
                    } else {
                        it
                    }
                }
                memory.write(lvalue, rvalue.asExpr(value.sort), trueExpr)
                newInst(nextInst)
            }
        )
    }

    private fun mkMapUpdate(buf: ByteBuffer) = with(ctx) {
        val mapVar = readVar(buf)
        val mapType = mapVar.underlyingType
        val map = mapVar.expr.asExpr(addressSort)
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

    private fun mkTypeAssert(buf: ByteBuffer) = with(ctx) {
        val l = readVar(buf)
        val iface = readVar(buf)
        val type = buf.long
        val commaOk = buf.bool

        val v = readVar(buf)
        val length = buf.int
        val arr = Array(length) { buf.int }
        val s = String(arr.map { it.toChar() }.toCharArray())

        println("$l $iface $type $commaOk $v $s $addressSort")
    }

    private fun mkMakeClosure(buf: ByteBuffer) = with(ctx) {
        val function = readVar(buf)
        val method = buf.long
        val bindings = Array(buf.int) { readVar(buf).expr }

        setFreeVariables(method, bindings)

        val lvalue = URegisterStackLValue(function.sort, function.index)
        scope.doWithState {
            memory.write(lvalue, function.expr, trueExpr)
        }
    }

    private fun mkTuple(state: GoState, type: GoType, vararg fields: UExpr<out USort>): UHeapRef = with(ctx) {
        val ref = state.memory.allocConcrete(type)
        for ((index, field) in fields.withIndex()) {
            state.memory.write(UFieldLValue(field.sort, ref, index), field.asExpr(field.sort), trueExpr)
        }
        return ref
    }

    fun getLastBlock(): Int {
        return scope.calcOnState { lastBlock }
    }

    private fun setLastBlock(block: Int) {
        scope.doWithState { lastBlock = block }
    }

    private fun copyVar(buf: ByteBuffer) = with(ctx) {
        val l = readVar(buf)
        val rvalue = readVar(buf).expr
        val lvalue = URegisterStackLValue(l.sort, l.index)

        scope.doWithState {
            memory.write(lvalue, rvalue, trueExpr)
        }
    }

    private fun copyVarWithTypeConstraint(buf: ByteBuffer) = with(ctx) {
        val l = readVar(buf)
        val rvalue = readVar(buf).expr
        val lvalue = URegisterStackLValue(l.sort, l.index)

        scope.doWithState {
            if (rvalue.sort == addressSort) {
                val ref = rvalue.asExpr(addressSort)
                scope.assert(memory.types.evalIsSubtype(ref, l.type)) ?: throw IllegalStateException()
            }
            memory.write(lvalue, rvalue, trueExpr)
        }
    }

    private fun copyMap(
        mapVar: Var,
        keySort: USort,
        valueSort: USort,
    ): UHeapRef = with(ctx) {
        val srcMap = mapVar.expr.asExpr(addressSort)
        checkNotNull(srcMap) ?: throw IllegalStateException()

        val mapType = mapVar.underlyingType
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

    private fun readVar(buf: ByteBuffer): Var = with(ctx) {
        val kind = VarKind.valueOf(buf.byte)

        val type = buf.long
        val underlyingType = buf.long

        val goSort = GoSort.valueOf(buf.byte)
        val sort = mapSort(goSort)

        var index = 0
        val expr = when (kind) {
            VarKind.CONST -> readConst(buf, type, sort)
            VarKind.PARAMETER, VarKind.FREE_VARIABLE, VarKind.LOCAL -> scope.calcOnState {
                index = resolveIndex(kind, buf.int)
                memory.read(URegisterStackLValue(sort, index))
            }

            else -> throw UnknownVarKindException()
        }
        return Var(expr.asExpr(sort), type, underlyingType, goSort, sort, index)
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
                val length = buf.int
                val content = Array(length) { mkBv(buf.int) }.asSequence()
                memory.allocateArrayInitialized(type, bv32Sort, sizeSort, content)
            }

            else -> throw UnknownTypeException()
        }
    }

    private fun readArrayReading(buf: ByteBuffer): ArrayReading = with(ctx) {
        val varIndex = resolveIndex(VarKind.LOCAL, buf.int)
        val valueType = buf.long
        val valueSort = mapSort(GoSort.valueOf(buf.byte))

        val array = readVar(buf)
        val arrayRef = array.expr.let {
            if (it.sort == pointerSort) {
                deref(it, addressSort)
            } else {
                it.asExpr(addressSort)
            }
        }
        val arrayType = array.underlyingType.let { getSliceType(it) ?: it }

        val indexVar = readVar(buf)
        val index = mkNarrow(bv(indexVar.expr), Int.SIZE_BITS, indexVar.goSort.isSigned()).asExpr(sizeSort)
        val length = scope.calcOnState { memory.readArrayLength(arrayRef, arrayType, sizeSort) }

        checkNotNull(arrayRef) ?: throw IllegalStateException()
        checkNegativeIndex(index) ?: throw IllegalStateException()
        checkIndexOutOfBounds(index, length) ?: throw IllegalStateException()

        return ArrayReading(arrayRef, arrayType, valueType, valueSort, varIndex, index)
    }

    private fun readFieldReading(buf: ByteBuffer): FieldReading = with(ctx) {
        val index = resolveIndex(VarKind.LOCAL, buf.int)
        val type = buf.long
        val sort = mapSort(GoSort.valueOf(buf.byte))

        val obj = readVar(buf).expr.let {
            if (it.sort == pointerSort) {
                deref(it, addressSort)
            } else {
                it.asExpr(addressSort)
            }
        }
        val field = buf.int

        checkNotNull(obj) ?: throw IllegalStateException()

        return FieldReading(obj, type, sort, index, field)
    }

    private fun resolveIndex(kind: VarKind, value: Int): Int = with(ctx) {
        val method = scope.calcOnState { lastEnteredMethod }
        when (kind) {
            VarKind.PARAMETER -> value
            VarKind.FREE_VARIABLE -> value + getArgsCount(method)
            VarKind.LOCAL -> value + getArgsCount(method) + getFreeVariablesCount(method)
            else -> -1
        }
    }

    private fun normalize(expr: UExpr<USort>, op: BinOp, signed: Boolean): UExpr<USort> = with(ctx) {
        when (op) {
            BinOp.SHR -> bv(expr).mkNarrow(Long.SIZE_BITS, signed).asExpr(bv64Sort)
            else -> expr
        }
    }

    private fun bv(expr: UExpr<USort>): UExpr<UBvSort> {
        return expr.asExpr(expr.sort as UBvSort)
    }

    private fun <Sort : USort> deref(pointer: UExpr<out USort>, sort: Sort): UExpr<Sort> {
        return scope.calcOnState {
            memory.read(pointerLValue(pointer, sort))
        }
    }

    private fun <Sort : USort> pointerLValue(pointer: UExpr<out USort>, sort: Sort): ULValue<*, Sort> = with(ctx) {
        return scope.calcOnState {
            pointer as UAddressPointer

            val lvalue = GoPointerLValue(mkConcreteHeapRef(pointer.address), sort)
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

    private fun checkIndexOutOfBounds(index: UExpr<USizeSort>, length: UExpr<USizeSort>): Unit? = with(ctx) {
        scope.fork(mkBvSignedLessExpr(index, length), blockOnFalseState = {
            methodResult = GoMethodResult.Panic("index out of bounds")
        })
    }

    private fun checkNegativeIndex(value: UExpr<USizeSort>): Unit? = with(ctx) {
        scope.fork(mkBvSignedGreaterOrEqualExpr(value, mkBv(0)), blockOnFalseState = {
            methodResult = GoMethodResult.Panic("negative index")
        })
    }

    private fun checkLength(length: UExpr<USizeSort>): Unit? = with(ctx) {
        scope.fork(mkBvSignedGreaterOrEqualExpr(length, mkBv(0)), blockOnFalseState = {
            methodResult = GoMethodResult.Panic("length < 0")
        })
    }

    private fun checkSliceToArrayPointerLength(
        sliceLength: UExpr<USizeSort>,
        arrayLength: UExpr<USizeSort>
    ): Unit? = with(ctx) {
        scope.fork(mkSizeGeExpr(sliceLength, arrayLength), blockOnFalseState = {
            methodResult = GoMethodResult.Panic("length of the slice is less than the length of the array")
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
    MK_SLICE_TO_ARRAY_POINTER(8),
    MK_MAKE_INTERFACE(9),
    MK_STORE(10),
    MK_IF(11),
    MK_ALLOC(12),
    MK_MAKE_SLICE(13),
    MK_MAKE_MAP(14),
    MK_EXTRACT(15),
    MK_SLICE(16),
    MK_RETURN(17),
    MK_PANIC(18),
    MK_VARIABLE(19),
    MK_RANGE(20),
    MK_NEXT(21),
    MK_POINTER_FIELD_READING(22),
    MK_FIELD_READING(23),
    MK_POINTER_ARRAY_READING(24),
    MK_ARRAY_READING(25),
    MK_MAP_LOOKUP(26),
    MK_MAP_UPDATE(27),
    MK_TYPE_ASSERT(28),
    MK_MAKE_CLOSURE(29);

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
    FREE_VARIABLE(3),
    LOCAL(4);

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
    val underlyingType: GoType,
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