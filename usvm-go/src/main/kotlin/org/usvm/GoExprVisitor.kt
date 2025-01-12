package org.usvm

import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KConst
import io.ksmt.utils.asExpr
import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoAddExpr
import org.jacodb.go.api.GoAllocExpr
import org.jacodb.go.api.GoAndExpr
import org.jacodb.go.api.GoAndNotExpr
import org.jacodb.go.api.GoBinaryExpr
import org.jacodb.go.api.GoBool
import org.jacodb.go.api.GoBuiltin
import org.jacodb.go.api.GoCallExpr
import org.jacodb.go.api.GoChangeInterfaceExpr
import org.jacodb.go.api.GoChangeTypeExpr
import org.jacodb.go.api.GoConst
import org.jacodb.go.api.GoConvertExpr
import org.jacodb.go.api.GoDivExpr
import org.jacodb.go.api.GoEqlExpr
import org.jacodb.go.api.GoExprVisitor
import org.jacodb.go.api.GoExtractExpr
import org.jacodb.go.api.GoFieldAddrExpr
import org.jacodb.go.api.GoFieldExpr
import org.jacodb.go.api.GoFloat32
import org.jacodb.go.api.GoFloat64
import org.jacodb.go.api.GoFreeVar
import org.jacodb.go.api.GoFunction
import org.jacodb.go.api.GoGeqExpr
import org.jacodb.go.api.GoGlobal
import org.jacodb.go.api.GoGtrExpr
import org.jacodb.go.api.GoIndexAddrExpr
import org.jacodb.go.api.GoIndexExpr
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoInt
import org.jacodb.go.api.GoInt16
import org.jacodb.go.api.GoInt32
import org.jacodb.go.api.GoInt64
import org.jacodb.go.api.GoInt8
import org.jacodb.go.api.GoLeqExpr
import org.jacodb.go.api.GoLookupExpr
import org.jacodb.go.api.GoLssExpr
import org.jacodb.go.api.GoMakeChanExpr
import org.jacodb.go.api.GoMakeClosureExpr
import org.jacodb.go.api.GoMakeInterfaceExpr
import org.jacodb.go.api.GoMakeMapExpr
import org.jacodb.go.api.GoMakeSliceExpr
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoModExpr
import org.jacodb.go.api.GoMulExpr
import org.jacodb.go.api.GoMultiConvertExpr
import org.jacodb.go.api.GoNeqExpr
import org.jacodb.go.api.GoNextExpr
import org.jacodb.go.api.GoNullConstant
import org.jacodb.go.api.GoOrExpr
import org.jacodb.go.api.GoParameter
import org.jacodb.go.api.GoPhiExpr
import org.jacodb.go.api.GoRangeExpr
import org.jacodb.go.api.GoSelectExpr
import org.jacodb.go.api.GoShlExpr
import org.jacodb.go.api.GoShrExpr
import org.jacodb.go.api.GoSliceExpr
import org.jacodb.go.api.GoSliceToArrayPointerExpr
import org.jacodb.go.api.GoStringConstant
import org.jacodb.go.api.GoSubExpr
import org.jacodb.go.api.GoTypeAssertExpr
import org.jacodb.go.api.GoUInt
import org.jacodb.go.api.GoUInt16
import org.jacodb.go.api.GoUInt32
import org.jacodb.go.api.GoUInt64
import org.jacodb.go.api.GoUInt8
import org.jacodb.go.api.GoUnArrowExpr
import org.jacodb.go.api.GoUnMulExpr
import org.jacodb.go.api.GoUnNotExpr
import org.jacodb.go.api.GoUnSubExpr
import org.jacodb.go.api.GoUnXorExpr
import org.jacodb.go.api.GoUnaryExpr
import org.jacodb.go.api.GoValue
import org.jacodb.go.api.GoVar
import org.jacodb.go.api.GoXorExpr
import org.jacodb.go.api.MapType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.SliceType
import org.jacodb.go.api.TupleType
import org.usvm.api.UnknownBinaryOperationException
import org.usvm.api.UnknownFunctionException
import org.usvm.api.UnknownUnaryOperationException
import org.usvm.api.UnsupportedUnaryOperationException
import org.usvm.api.allocateArray
import org.usvm.api.collection.ObjectMapCollectionApi.ensureObjectMapSizeCorrect
import org.usvm.api.collection.ObjectMapCollectionApi.mkSymbolicObjectMap
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapAnyKey
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapGet
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapMergeInto
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapRemove
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapSize
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapAnyKey
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapGet
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapMergeInto
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapRemove
import org.usvm.api.memcpy
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.api.readField
import org.usvm.api.refSetContainsElement
import org.usvm.api.setContainsElement
import org.usvm.api.typeStreamOf
import org.usvm.api.writeArrayIndex
import org.usvm.api.writeField
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.interpreter.GoStepScope
import org.usvm.memory.GoPointerLValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.operator.GoBinaryOperator
import org.usvm.operator.GoUnaryOperator
import org.usvm.operator.mkNarrow
import org.usvm.state.GoMethodResult
import org.usvm.statistics.ApplicationGraph
import org.usvm.type.GoBasicTypes
import org.usvm.type.underlying
import org.usvm.types.first

class GoExprVisitor(
    private val ctx: GoContext,
    private val pkg: GoPackage,
    private val scope: GoStepScope,
    private val applicationGraph: ApplicationGraph<GoMethod, GoInst>,
) : GoExprVisitor<UExpr<out USort>> {
    override fun visitGoCallExpr(expr: GoCallExpr): UExpr<out USort> {
        val func = expr.value
        if (func is GoBuiltin) {
            return callBuiltin(func, expr.args)
        }

        val result = scope.calcOnState { methodResult }
        if (result is GoMethodResult.Success) {
            scope.doWithState { methodResult = GoMethodResult.NoCall }
            if (result.method.metName == INIT_FUNCTION) {
                return ctx.noValue
            }
            return result.value
        }

        val args = expr.args.let { if (expr.callee == null) it else listOf(func)+it }
        val method = when {
            expr.callee != null -> {
                val instance = func.accept(this).asExpr(ctx.addressSort)
                val type = scope.calcOnState {
                    scope.assert(memory.types.evalIsSubtype(instance, func.type)) ?: throw IllegalStateException()
                    memory.typeStreamOf(instance).first()
                }
                pkg.findMethod("(${type.typeName}).${expr.callee!!.name}")
            }

            func is GoFunction -> pkg.findMethod(func.metName)
            func is GoVar -> scope.calcOnState {
                pkg.findMethod((memory.read(URegisterStackLValue(ctx.addressSort, index(func.name))) as KConst).decl.name)
            }

            else -> throw UnknownFunctionException(func.toString())
        }
        val parameters = args.map { it.accept(this) }.toTypedArray()
        val call = GoCall(method, applicationGraph.entryPoints(method).first(), parameters)
        ctx.setMethodInfo(method, parameters)

        scope.doWithState {
            addCall(call, currentStatement)
        }
        return ctx.noValue
    }

    override fun visitGoAllocExpr(expr: GoAllocExpr): UExpr<out USort> {
        return scope.calcOnState {
            mkPointer(expr.type)
        }
    }

    override fun visitGoPhiExpr(expr: GoPhiExpr): UExpr<out USort> {
        val currentBlock = expr.location.index
        val lastBlock = scope.calcOnState {
            var node = pathNode
            while (node.statement.location.index == currentBlock) {
                node = node.parent!!
            }
            node.statement.location.index
        }
        val block = expr.location.method.blocks[currentBlock]

        block.predecessors.forEachIndexed { i, pred ->
            if (lastBlock != pred) {
                return@forEachIndexed
            }

            return expr.edges[i].accept(this)
        }
        return ctx.nullRef
    }

    override fun visitGoAddExpr(expr: GoAddExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoSubExpr(expr: GoSubExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoMulExpr(expr: GoMulExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoDivExpr(expr: GoDivExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoModExpr(expr: GoModExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoAndExpr(expr: GoAndExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoOrExpr(expr: GoOrExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoXorExpr(expr: GoXorExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoShlExpr(expr: GoShlExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoShrExpr(expr: GoShrExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoAndNotExpr(expr: GoAndNotExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoEqlExpr(expr: GoEqlExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoNeqExpr(expr: GoNeqExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoLssExpr(expr: GoLssExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoLeqExpr(expr: GoLeqExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoGtrExpr(expr: GoGtrExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoGeqExpr(expr: GoGeqExpr): UExpr<out USort> = visitGoBinaryExpr(expr)

    override fun visitGoUnNotExpr(expr: GoUnNotExpr): UExpr<out USort> = visitGoUnaryExpr(expr)

    override fun visitGoUnSubExpr(expr: GoUnSubExpr): UExpr<out USort> = visitGoUnaryExpr(expr)

    override fun visitGoUnArrowExpr(expr: GoUnArrowExpr): UExpr<out USort> = visitGoUnaryExpr(expr)

    override fun visitGoUnMulExpr(expr: GoUnMulExpr): UExpr<out USort> = visitGoUnaryExpr(expr)

    override fun visitGoUnXorExpr(expr: GoUnXorExpr): UExpr<out USort> = visitGoUnaryExpr(expr)

    override fun visitGoChangeTypeExpr(expr: GoChangeTypeExpr): UExpr<out USort> {
        val result = expr.operand.accept(this)
        if (result.sort == ctx.addressSort) {
            scope.doWithState {
                scope.assert(memory.types.evalIsSubtype(result.asExpr(ctx.addressSort), expr.type)) ?: throw IllegalStateException()
            }
        }
        return result
    }

    override fun visitGoConvertExpr(expr: GoConvertExpr): UExpr<out USort> {
        return ctx.mkPrimitiveCast(expr.operand.accept(this), ctx.typeToSort(expr.type))
    }

    override fun visitGoMultiConvertExpr(expr: GoMultiConvertExpr): UExpr<out USort> {
        // this is something about generics?
        return unsupportedExpr("MultiConvert")
    }

    override fun visitGoChangeInterfaceExpr(expr: GoChangeInterfaceExpr): UExpr<out USort> {
        return expr.operand.accept(this)
    }

    override fun visitGoSliceToArrayPointerExpr(expr: GoSliceToArrayPointerExpr): UExpr<out USort> {
        val slice = expr.operand.accept(this).asExpr(ctx.addressSort)
        val sliceType = expr.operand.type as SliceType
        val sliceLength = scope.calcOnState { memory.readArrayLength(slice, sliceType, ctx.sizeSort) }

        val arrayType = (expr.type as PointerType).baseType as ArrayType
        val arrayLength = ctx.mkSizeExpr(arrayType.len.toInt())

        checkNotNull(slice) ?: throw IllegalStateException()
        checkSliceToArrayPointerLength(sliceLength, arrayLength) ?: throw IllegalStateException()

        return scope.calcOnState {
            val array = memory.allocateArray(arrayType, ctx.sizeSort, arrayLength)
            for (i in 0 until arrayType.len) {
                val idx = ctx.mkSizeExpr(i.toInt())
                val element = memory.readArrayIndex(slice, idx, sliceType, ctx.typeToSort(sliceType.elementType))
                memory.writeArrayIndex(array, idx, arrayType, ctx.typeToSort(arrayType.elementType), element, ctx.trueExpr)
            }

            memory.write(GoPointerLValue(array, ctx.addressSort), array, ctx.trueExpr)
            ctx.mkAddressPointer(array.address)
        }
    }

    override fun visitGoMakeInterfaceExpr(expr: GoMakeInterfaceExpr): UExpr<out USort> {
        val value = expr.value.accept(this).let {
            when (it.sort) {
                ctx.addressSort -> it
                else -> scope.calcOnState {
                    val ref = memory.allocConcrete(expr.type)
                    memory.writeField(ref, BOXED_VALUE_FIELD, it.sort, it.asExpr(it.sort), ctx.trueExpr)
                    ref
                }
            }
        }.asExpr(ctx.addressSort)

        scope.doWithState {
            scope.assert(memory.types.evalIsSubtype(value, expr.type)) ?: throw IllegalStateException()
        }

        return value
    }

    override fun visitGoMakeClosureExpr(expr: GoMakeClosureExpr): UExpr<out USort> {
        return ctx.mkConst(expr.func.name, ctx.addressSort)
    }

    override fun visitGoMakeMapExpr(expr: GoMakeMapExpr): UExpr<out USort> {
        val reserve = expr.reserve.accept(this).asExpr(ctx.sizeSort)

        checkLength(reserve) ?: throw IllegalStateException()

        return scope.calcOnState {
            val ref = memory.allocConcrete(expr.type)
            memory.write(UMapLengthLValue(ref, expr.type, ctx.sizeSort), reserve, ctx.trueExpr)
            ref.asExpr(ctx.addressSort)
        }
    }

    override fun visitGoMakeChanExpr(expr: GoMakeChanExpr): UExpr<out USort> {
        // channels aren't supported now
        return unsupportedExpr("MakeChan")
    }

    override fun visitGoMakeSliceExpr(expr: GoMakeSliceExpr): UExpr<out USort> {
        val len = expr.len.accept(this).asExpr(ctx.sizeSort)

        checkLength(len) ?: throw IllegalStateException()

        return scope.calcOnState {
            memory.allocateArray(expr.type, ctx.sizeSort, len)
        }
    }

    override fun visitGoSliceExpr(expr: GoSliceExpr): UExpr<out USort> {
        val collection = expr.array.accept(this).let { if (it.sort == ctx.addressSort) it else deref(it, ctx.addressSort) }.asExpr(ctx.addressSort)
        val type = expr.array.type.let { if (it is PointerType) it.baseType else it }
        val length = scope.calcOnState { memory.readArrayLength(collection, type, ctx.sizeSort) }
        val low = expr.low.accept(this).asExpr(ctx.sizeSort)
        val high = expr.high.accept(this).let { if (it is KBitVec32Value && it.intValue == -1) length else it }.asExpr(ctx.sizeSort)
        val count = ctx.mkSizeSubExpr(high, low)
        val elementType = when (type) {
            is ArrayType -> type.elementType
            is SliceType -> type.elementType
            is BasicType -> GoBasicTypes.UINT8
            else -> throw IllegalStateException("illegal type for collection")
        }
        val elementSort = ctx.typeToSort(elementType)

        checkNotNull(collection) ?: throw IllegalStateException()
        checkNegativeIndex(low) ?: throw IllegalStateException()
        checkNegativeIndex(high) ?: throw IllegalStateException()
        checkIndexOutOfBounds(low, ctx.mkSizeAddExpr(high, ctx.mkSizeExpr(1))) ?: throw IllegalStateException()
        checkIndexOutOfBounds(high, ctx.mkSizeAddExpr(length, ctx.mkSizeExpr(1))) ?: throw IllegalStateException()

        return scope.calcOnState {
            val result = memory.allocateArray(expr.type, ctx.sizeSort, count)
            when (type) {
                is ArrayType -> {
                    val array = memory.allocateArray(expr.type, ctx.sizeSort, count)
                    for (i in 0 until type.len) {
                        val idx = ctx.mkSizeExpr(i.toInt())
                        val element = memory.readArrayIndex(collection, idx, type, elementSort)
                        memory.writeArrayIndex(array, idx, expr.type, elementSort, element, ctx.trueExpr)
                    }
                    memory.memcpy(array, result, expr.type, elementSort, low, ctx.mkSizeExpr(0), count)
                }

                else -> memory.memcpy(collection, result, expr.type, elementSort, low, ctx.mkSizeExpr(0), count)
            }
            result
        }
    }

    override fun visitGoFieldAddrExpr(expr: GoFieldAddrExpr): UExpr<out USort> {
        if (expr.instance is GoNullConstant) {
            return scope.calcOnState {
                panic("nil struct")
                ctx.noValue
            }
        }

        val struct = deref(expr.instance.accept(this).asExpr(ctx.pointerSort), ctx.addressSort)
        checkNotNull(struct) ?: throw IllegalStateException()
        return scope.calcOnState {
            val fieldType = (expr.type as PointerType).baseType
            val fieldLValue = UFieldLValue(ctx.typeToSort(fieldType), struct, expr.field)
            mkPointer(fieldType, ctx.mkLValuePointer(fieldLValue))
        }
    }

    override fun visitGoFieldExpr(expr: GoFieldExpr): UExpr<out USort> {
        val struct = expr.instance.accept(this).asExpr(ctx.addressSort)
        checkNotNull(struct) ?: throw IllegalStateException()
        return scope.calcOnState {
            memory.readField(struct, expr.field, ctx.typeToSort(expr.type))
        }
    }

    override fun visitGoIndexAddrExpr(expr: GoIndexAddrExpr): UExpr<out USort> {
        val (array, index) = visitIndexExpr(expr.instance, expr.index)
        val arrayType = expr.instance.type.let { if (it is PointerType) it.baseType else it }
        return scope.calcOnState {
            val elementType = (expr.type as PointerType).baseType
            val elementLValue = UArrayIndexLValue(ctx.typeToSort(elementType), array, index, arrayType)
            mkPointer(elementType, ctx.mkLValuePointer(elementLValue))
        }
    }

    override fun visitGoIndexExpr(expr: GoIndexExpr): UExpr<out USort> {
        val (array, index) = visitIndexExpr(expr.instance, expr.index)
        return scope.calcOnState {
            memory.readArrayIndex(array, index, expr.instance.type, ctx.typeToSort(expr.type))
        }
    }

    override fun visitGoLookupExpr(expr: GoLookupExpr): UExpr<out USort> {
        val map = expr.instance.accept(this).asExpr(ctx.addressSort)
        val type = expr.instance.type as MapType
        val key = expr.index.accept(this)

        val isRefKey = key.sort == ctx.addressSort
        val commaOk = expr.commaOk
        val valueSort = ctx.typeToSort(type.valueType)

        checkNotNull(map) ?: throw IllegalStateException()
        scope.ensureObjectMapSizeCorrect(map, type) ?: throw IllegalStateException()

        val contains = scope.calcOnState {
            if (isRefKey) {
                memory.refSetContainsElement(map, key.asExpr(ctx.addressSort), type)
            } else {
                memory.setContainsElement(map, key, type, USizeExprKeyInfo())
            }
        }
        val lvalue = if (isRefKey) {
            URefMapEntryLValue(valueSort, map, key.asExpr(ctx.addressSort), type)
        } else {
            UMapEntryLValue(key.sort, valueSort, map, key.asExpr(key.sort), type, USizeExprKeyInfo())
        }
        val rvalue = scope.calcOnState { memory.read(lvalue).asExpr(valueSort) }

        return scope.calcOnState {
            rvalue.let { if (commaOk) mkTuple(TupleType(listOf(type.valueType, GoBasicTypes.BOOL)), it, contains) else it }
        }
    }

    override fun visitGoSelectExpr(expr: GoSelectExpr): UExpr<out USort> {
        // channels aren't supported now
        return unsupportedExpr("Select")
    }

    override fun visitGoRangeExpr(expr: GoRangeExpr): UExpr<out USort> {
        val collection = expr.instance.accept(this).asExpr(ctx.addressSort).let {
            when (val type = expr.instance.type) {
                is MapType -> copyMap(it, type)
                is BasicType -> it
                else -> throw IllegalStateException("illegal type for range")
            }
        }
        return scope.calcOnState {
            mkTuple(
                TupleType(listOf(expr.instance.type, GoBasicTypes.INT32)),
                collection, ctx.mkSizeExpr(0)
            )
        }
    }

    override fun visitGoNextExpr(expr: GoNextExpr): UExpr<out USort> {
        val iter = expr.instance.accept(this).asExpr(ctx.addressSort)
        return scope.calcOnState {
            val tupleType = memory.typeStreamOf(iter).commonSuperType as TupleType
            val collection = memory.readField(iter, 0, ctx.addressSort)
            val notNull = ctx.mkNot(ctx.mkHeapRefEq(collection, ctx.nullRef))
            when (val collectionType = tupleType.types[0]) {
                is BasicType -> {
                    val index = memory.readField(iter, 1, ctx.sizeSort)
                    val char = memory.readArrayIndex(collection, index, collectionType, ctx.bv8Sort)
                    val length = memory.readArrayLength(collection, collectionType, ctx.sizeSort)
                    val ok = ctx.mkAnd(notNull, ctx.mkBvSignedLessExpr(index, length))

                    memory.writeField(iter, 1, ctx.sizeSort, ctx.mkBvAddExpr(index, ctx.mkSizeExpr(1)), ctx.trueExpr)
                    mkTuple(
                        TupleType(listOf(GoBasicTypes.BOOL, GoBasicTypes.INT32, GoBasicTypes.INT32)),
                        ok, index, ctx.mkPrimitiveCast(char, ctx.bv32Sort)
                    )
                }

                is MapType -> {
                    scope.ensureObjectMapSizeCorrect(collection, collectionType) ?: throw IllegalStateException()

                    val length = symbolicObjectMapSize(collection, collectionType)
                    val ok = ctx.mkAnd(notNull, ctx.mkBvSignedGreaterExpr(length, ctx.mkBv(0)))
                    val isPrimitiveKey = collectionType.keyType.underlying() is BasicType
                    val (key, value) = if (isPrimitiveKey) {
                        val k = symbolicPrimitiveMapAnyKey(collection, collectionType, ctx.typeToSort(collectionType.keyType), USizeExprKeyInfo())
                        val v = symbolicPrimitiveMapGet(collection, k, collectionType, ctx.typeToSort(collectionType.valueType), USizeExprKeyInfo())
                        symbolicPrimitiveMapRemove(collection, k, collectionType, USizeExprKeyInfo())
                        Pair(k, v)
                    } else {
                        val k = symbolicObjectMapAnyKey(collection, collectionType)
                        val v = symbolicObjectMapGet(collection, k, collectionType, ctx.typeToSort(collectionType.valueType))
                        symbolicObjectMapRemove(collection, k, collectionType)
                        Pair(k, v)
                    }

                    mkTuple(
                        TupleType(listOf(GoBasicTypes.BOOL, collectionType.keyType, collectionType.valueType)),
                        ok, key, value
                    )
                }

                else -> throw IllegalStateException("invalid collection type in next expr")
            }
        }
    }

    override fun visitGoTypeAssertExpr(expr: GoTypeAssertExpr): UExpr<out USort> {
        val x = expr.instance.accept(this)

        val assertType = expr.assertType.let { if (it is TupleType) it.types[0] else it }
        val assertSort = ctx.typeToSort(assertType)

        val commaOk = expr.type is TupleType
        val tupleType = TupleType(listOf(assertType, GoBasicTypes.BOOL))

        val ite: (UHeapRef, UExpr<out USort>, UExpr<out USort>) -> UExpr<out USort> = { ref, ok, fail ->
            scope.calcOnState {
                return@calcOnState ctx.mkIte(
                    memory.types.evalIsSupertype(ref, assertType),
                    trueBranch = { ok.asExpr(ok.sort) },
                    falseBranch = { fail.asExpr(fail.sort).also { if (!commaOk) panic("type assertion failed") } }
                )
            }
        }

        val xAddr = x.asExpr(ctx.addressSort)
        checkNotNull(xAddr) ?: throw IllegalStateException()

        return scope.calcOnState {
            when (assertSort) {
                ctx.addressSort -> {
                    if (commaOk) {
                        mkTuple(tupleType, ite(xAddr, xAddr, ctx.nullRef), ite(xAddr, ctx.trueExpr, ctx.falseExpr))
                    } else {
                        ite(xAddr, xAddr, ctx.nullRef)
                    }
                }

                else -> {
                    val unboxedValue = memory.readField(xAddr, BOXED_VALUE_FIELD, assertSort)
                    val sample = assertSort.sampleUValue().asExpr(assertSort)

                    if (commaOk) {
                        mkTuple(tupleType, ite(xAddr, unboxedValue, sample), ite(xAddr, ctx.trueExpr, ctx.falseExpr))
                    } else {
                        ite(xAddr, unboxedValue, sample)
                    }
                }
            }
        }
    }

    override fun visitGoExtractExpr(expr: GoExtractExpr): UExpr<out USort> {
        val tuple = expr.instance.accept(this).asExpr(ctx.addressSort)

        return scope.calcOnState {
            memory.readField(tuple, expr.index, ctx.typeToSort(expr.type))
        }
    }

    override fun visitGoVar(expr: GoVar): UExpr<out USort> {
        return scope.calcOnState {
            memory.read(URegisterStackLValue(ctx.typeToSort(expr.type), index(expr.name)))
        }
    }

    override fun visitGoFreeVar(expr: GoFreeVar): UExpr<out USort> {
        return scope.calcOnState {
            memory.read(URegisterStackLValue(ctx.typeToSort(expr.type), expr.index + ctx.freeVariableOffset(lastEnteredMethod)))
        }
    }

    override fun visitGoParameter(expr: GoParameter): UExpr<out USort> {
        return scope.calcOnState {
            memory.read(URegisterStackLValue(ctx.typeToSort(expr.type), expr.index))
        }
    }

    override fun visitGoConst(expr: GoConst): UExpr<out USort> {
        // const can't be visited
        return unsupportedExpr("Const")
    }

    override fun visitGoGlobal(expr: GoGlobal): UExpr<out USort> {
        return scope.calcOnState {
            ctx.getGlobal(expr)
        }
    }

    override fun visitGoBuiltin(expr: GoBuiltin): UExpr<out USort> {
        // builtin can't be stored in a variable
        return unsupportedExpr("Builtin")
    }

    override fun visitGoFunction(expr: GoFunction): UExpr<out USort> {
        return ctx.mkConst(expr.metName, ctx.addressSort)
    }

    override fun visitGoBool(value: GoBool): UExpr<out USort> = with(ctx) {
        return mkBool(value.value)
    }

    override fun visitGoInt(value: GoInt): UExpr<out USort> = with(ctx) {
        return mkBv(value.value.toInt())
    }

    override fun visitGoInt8(value: GoInt8): UExpr<out USort> = with(ctx) {
        return mkBv(value.value)
    }

    override fun visitGoInt16(value: GoInt16): UExpr<out USort> = with(ctx) {
        return mkBv(value.value)
    }

    override fun visitGoInt32(value: GoInt32): UExpr<out USort> = with(ctx) {
        return mkBv(value.value)
    }

    override fun visitGoInt64(value: GoInt64): UExpr<out USort> = with(ctx) {
        return mkBv(value.value)
    }

    override fun visitGoUInt(value: GoUInt): UExpr<out USort> = with(ctx) {
        return mkBv(value.value.toInt())
    }

    override fun visitGoUInt8(value: GoUInt8): UExpr<out USort> = with(ctx) {
        return mkBv(value.value.toByte())
    }

    override fun visitGoUInt16(value: GoUInt16): UExpr<out USort> = with(ctx) {
        return mkBv(value.value.toShort())
    }

    override fun visitGoUInt32(value: GoUInt32): UExpr<out USort> = with(ctx) {
        return mkBv(value.value.toInt())
    }

    override fun visitGoUInt64(value: GoUInt64): UExpr<out USort> = with(ctx) {
        return mkBv(value.value.toLong())
    }

    override fun visitGoFloat32(value: GoFloat32): UExpr<out USort> = with(ctx) {
        return mkFp(value.value, fp32Sort)
    }

    override fun visitGoFloat64(value: GoFloat64): UExpr<out USort> = with(ctx) {
        return mkFp(value.value, fp64Sort)
    }

    override fun visitGoNullConstant(value: GoNullConstant): UExpr<out USort> = ctx.nullRef

    override fun visitGoStringConstant(value: GoStringConstant): UExpr<out USort> {
        return scope.calcOnState {
            mkString(value.value)
        }
    }

    fun checkNotNull(obj: UHeapRef): Unit? = with(ctx) {
        scope.fork(mkHeapRefEq(obj, nullRef).not(), blockOnFalseState = {
            panic("null")
        })
    }

    private fun visitGoBinaryExpr(expr: GoBinaryExpr): UExpr<out USort> {
        val signed = expr.lhv.type is BasicType && !expr.lhv.type.typeName.startsWith("ui")
        return when (expr) {
            is GoAddExpr -> GoBinaryOperator.Add
            is GoSubExpr -> GoBinaryOperator.Sub
            is GoMulExpr -> GoBinaryOperator.Mul
            is GoDivExpr -> GoBinaryOperator.Div(signed)
            is GoModExpr -> GoBinaryOperator.Mod(signed)
            is GoAndExpr -> GoBinaryOperator.And
            is GoOrExpr -> GoBinaryOperator.Or
            is GoXorExpr -> GoBinaryOperator.Xor
            is GoShlExpr -> GoBinaryOperator.Shl
            is GoShrExpr -> GoBinaryOperator.Shr(signed)
            is GoAndNotExpr -> GoBinaryOperator.AndNot
            is GoEqlExpr -> GoBinaryOperator.Eql
            is GoLssExpr -> GoBinaryOperator.Lss(signed)
            is GoGtrExpr -> GoBinaryOperator.Gtr(signed)
            is GoNeqExpr -> GoBinaryOperator.Neq
            is GoLeqExpr -> GoBinaryOperator.Leq(signed)
            is GoGeqExpr -> GoBinaryOperator.Geq(signed)
            else -> throw UnknownBinaryOperationException(expr.toString())
        }(expr.lhv.accept(this), normalize(expr.rhv.accept(this), expr, signed))
    }

    private fun visitGoUnaryExpr(expr: GoUnaryExpr): UExpr<out USort> {
        val x = expr.value.accept(this)
        return when (expr) {
            is GoUnArrowExpr -> throw UnsupportedUnaryOperationException("channel operations")
            is GoUnXorExpr, is GoUnNotExpr, is GoUnSubExpr -> GoUnaryOperator.Neg(x)
            is GoUnMulExpr -> deref(x, ctx.typeToSort(expr.type))
            else -> throw UnknownUnaryOperationException(expr.toString())
        }
    }

    private fun visitIndexExpr(instance: GoValue, idx: GoValue): Pair<UHeapRef, UExpr<USizeSort>> {
        val array = instance.accept(this).let { if (it.sort == ctx.addressSort) it else deref(it, ctx.addressSort) }.asExpr(ctx.addressSort)
        val type = instance.type.let { if (it is PointerType) it.baseType else it }
        val index = idx.accept(this).asExpr(ctx.sizeSort)
        val length = scope.calcOnState { memory.readArrayLength(array, type, ctx.sizeSort) }

        scope.assert(ctx.mkSizeGeExpr(length, ctx.mkSizeExpr(0)))
        checkNotNull(array) ?: throw IllegalStateException()
        checkNegativeIndex(index) ?: throw IllegalStateException()
        checkIndexOutOfBounds(index, length) ?: throw IllegalStateException()

        return Pair(array, index)
    }

    private fun normalize(expr: UExpr<out USort>, goExpr: GoBinaryExpr, signed: Boolean): UExpr<out USort> = with(ctx) {
        when (goExpr) {
            is GoShrExpr -> bv(expr).mkNarrow(Long.SIZE_BITS, signed).asExpr(bv64Sort)
            else -> expr
        }
    }

    private fun bv(expr: UExpr<out USort>): UExpr<UBvSort> {
        return expr.asExpr(expr.sort as UBvSort)
    }

    private fun <Sort : USort> deref(expr: UExpr<out USort>, sort: Sort): UExpr<Sort> = with(ctx) {
        val pointer = expr.asExpr(pointerSort) as UAddressPointer

        return scope.calcOnState {
            memory.read(pointerLValue(pointer, sort))
        }
    }

    private fun index(name: String): Int {
        return name.substring(1).toInt() + ctx.localVariableOffset(scope.calcOnState { lastEnteredMethod })
    }

    private fun checkIndexOutOfBounds(index: UExpr<USizeSort>, length: UExpr<USizeSort>): Unit? = with(ctx) {
        scope.fork(mkSizeLtExpr(index, length), blockOnFalseState = {
            panic("index out of bounds")
        })
    }

    private fun checkNegativeIndex(value: UExpr<USizeSort>): Unit? = with(ctx) {
        scope.fork(mkSizeGeExpr(value, mkSizeExpr(0)), blockOnFalseState = {
            panic("negative index")
        })
    }

    private fun checkLength(length: UExpr<USizeSort>): Unit? = with(ctx) {
        scope.fork(mkSizeGeExpr(length, mkSizeExpr(0)), blockOnFalseState = {
            panic("length < 0")
        })
    }

    private fun checkSliceToArrayPointerLength(
        sliceLength: UExpr<USizeSort>,
        arrayLength: UExpr<USizeSort>
    ): Unit? = with(ctx) {
        scope.fork(mkSizeGeExpr(sliceLength, arrayLength), blockOnFalseState = {
            panic("length of the slice is less than the length of the array")
        })
    }

    private fun callBuiltin(method: GoBuiltin, args: List<GoValue>): UExpr<out USort> {
        return when (method.name) {
            "len", "cap" -> {
                val arg = args[0]
                val collection = arg.accept(this).asExpr(ctx.addressSort)

                return ctx.mkIte(
                    ctx.mkNot(ctx.mkHeapRefEq(collection, ctx.nullRef)),
                    trueBranch = {
                        scope.calcOnState {
                            when (val type = arg.type) {
                                is ArrayType, is SliceType, is BasicType -> memory.readArrayLength(collection, type, ctx.sizeSort)
                                is MapType -> {
                                    scope.ensureObjectMapSizeCorrect(collection, type) ?: throw IllegalStateException()
                                    symbolicObjectMapSize(collection, type)
                                }

                                else -> throw IllegalStateException()
                            }
                        }
                    },
                    falseBranch = {
                        ctx.mkSizeExpr(0)
                    },
                )
            }

            "panic" -> {
                val value = args[0].accept(this)
                scope.calcOnState {
                    panic(value, args[0].type)
                    ctx.noValue
                }
            }

            "recover" -> scope.calcOnState {
                recover()
            }

            else -> ctx.nullRef
        }
    }

    private fun copyMap(
        srcMap: UHeapRef,
        mapType: MapType,
    ): UHeapRef = with(ctx) {
        checkNotNull(srcMap) ?: throw IllegalStateException()
        scope.ensureObjectMapSizeCorrect(srcMap, mapType) ?: throw IllegalStateException()

        val keySort = typeToSort(mapType.keyType)
        val valueSort = typeToSort(mapType.valueType)
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

    private fun unsupportedExpr(name: String): UExpr<out USort> {
        throw UnsupportedOperationException("Expression '$name' not supported")
    }

    companion object {
        const val BOXED_VALUE_FIELD = "boxed_value"
    }
}