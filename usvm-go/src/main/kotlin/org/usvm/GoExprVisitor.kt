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
import org.jacodb.go.api.GoType
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
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.NullType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.SignatureType
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
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.operator.GoBinaryOperator
import org.usvm.operator.GoUnaryOperator
import org.usvm.operator.mkNarrow
import org.usvm.state.GoMethodResult
import org.usvm.state.GoState.Companion.POINTER_FIELD
import org.usvm.statistics.ApplicationGraph
import org.usvm.type.GoBasicTypes
import org.usvm.type.underlying
import org.usvm.types.first
import org.usvm.util.isInit

class GoExprVisitor(
    private val ctx: GoContext,
    private val program: GoProgram,
    private val scope: GoStepScope,
    private val applicationGraph: ApplicationGraph<GoMethod, GoInst>,
) : GoExprVisitor<UExpr<out USort>> {
    override fun visitGoCallExpr(expr: GoCallExpr): UExpr<out USort> {
        val func = expr.value
        if (func is GoBuiltin) {
            return callBuiltin(func, expr.args, expr.type)
        }
        if (func is GoParameter && expr.callee == null) {
            return mockCall(expr, func)
        }

        val result = scope.calcOnState { methodResult }
        if (result is GoMethodResult.Success) {
            scope.doWithState { methodResult = GoMethodResult.NoCall }
            if (result.method.isInit(expr.location)) {
                return ctx.noValue
            }
            return result.value
        }

        val args = expr.args.let { if (expr.callee == null) it else listOf(func) + it }
        val method = when {
            expr.callee != null -> {
                val instance = func.accept(this).asExpr(ctx.addressSort)
                val type = scope.calcOnState {
                    scope.assert(memory.types.evalIsSubtype(instance, func.type)) ?: throw IllegalStateException()
                    memory.typeStreamOf(instance).first()
                }
                program.findMethod(expr.location, "(${type.typeName}).${expr.callee!!.name}")
            }

            func is GoFunction -> program.findMethod(expr.location, func.metName)
            func is GoVar -> scope.calcOnState {
                program.findMethod(expr.location, (memory.read(URegisterStackLValue(ctx.addressSort, index(func.name))) as KConst).decl.name)
            }

            else -> throw UnknownFunctionException(func.toString())
        }
        if (method.blocks.isEmpty()) {
            return mockCall(expr, method)
        }

        val parameters = args.map { it.accept(this) }.toTypedArray()
        val call = GoCall(method, applicationGraph.entryPoints(method).first())
        ctx.setMethodInfo(method, parameters)

        scope.doWithState {
            addCall(call, currentStatement)
        }
        return ctx.noValue
    }

    override fun visitGoAllocExpr(expr: GoAllocExpr): UExpr<out USort> {
        return mkPointer(expr.type)
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
        return changeType(expr.operand.accept(this), expr.operand.type, expr.type)
    }

    override fun visitGoConvertExpr(expr: GoConvertExpr): UExpr<out USort> {
        val value = expr.operand.accept(this)
        return when (val type = expr.type) {
            GoBasicTypes.UNSAFE_POINTER -> ctx.mkBv(-1L)
            GoBasicTypes.STRING -> {
                val sliceType = expr.operand.type as SliceType
                when (sliceType.elementType) {
                    GoBasicTypes.RUNE -> ctx.nullRef // TODO
                    GoBasicTypes.UINT8 -> value
                    else -> throw IllegalStateException("only []rune and []int can be converted to string")
                }
            }

            is PointerType -> mkPointer(type)
            else -> {
                when (expr.operand.type) {
                    GoBasicTypes.UNSAFE_POINTER -> tryBox(ctx.mkBv(-1L), expr.type)
                    GoBasicTypes.STRING -> {
                        val sliceType = type as SliceType
                        when (sliceType.elementType) {
                            GoBasicTypes.RUNE -> ctx.nullRef // TODO
                            GoBasicTypes.UINT8 -> value
                            else -> throw IllegalStateException("string can be converted only to []rune and []int")
                        }
                    }

                    else -> ctx.mkPrimitiveCast(value, ctx.typeToSort(type))
                }
            }
        }
    }

    override fun visitGoMultiConvertExpr(expr: GoMultiConvertExpr): UExpr<out USort> {
        // this is something about generics?
        return unsupportedExpr("MultiConvert")
    }

    override fun visitGoChangeInterfaceExpr(expr: GoChangeInterfaceExpr): UExpr<out USort> {
        return expr.operand.accept(this)
    }

    override fun visitGoSliceToArrayPointerExpr(expr: GoSliceToArrayPointerExpr): UExpr<out USort> {
        val slice = unboxNamedRef(expr.operand.accept(this).asExpr(ctx.addressSort), expr.operand.type)
        val sliceType = expr.operand.type.underlying() as SliceType
        val sliceLength = scope.calcOnState { memory.readArrayLength(slice, sliceType, ctx.sizeSort) }

        val arrayType = (expr.type as PointerType).baseType.underlying() as ArrayType
        val arrayLength = ctx.mkSizeExpr(arrayType.len.toInt())

        checkLength(sliceLength) ?: throw IllegalStateException()
        checkNotNull(slice) ?: throw IllegalStateException()
        checkSliceToArrayPointerLength(sliceLength, arrayLength) ?: throw IllegalStateException()

        return scope.calcOnState {
            val array = memory.allocateArray(arrayType, ctx.sizeSort, arrayLength)
            for (i in 0 until arrayType.len) {
                val idx = ctx.mkSizeExpr(i.toInt())
                val element = memory.readArrayIndex(slice, idx, sliceType, ctx.typeToSort(sliceType.elementType))
                memory.writeArrayIndex(array, idx, arrayType, ctx.typeToSort(arrayType.elementType), element, ctx.trueExpr)
            }

            tryBox(mkPointer(arrayType, array), expr.type)
        }
    }

    override fun visitGoMakeInterfaceExpr(expr: GoMakeInterfaceExpr): UExpr<out USort> {
        val value = box(expr.value.accept(this@GoExprVisitor), expr.value.type)
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
        val mapType = expr.type.underlying()

        checkLength(reserve) ?: throw IllegalStateException()

        return scope.calcOnState {
            val ref = memory.allocConcrete(mapType)
            memory.write(UMapLengthLValue(ref, mapType, ctx.sizeSort), reserve, ctx.trueExpr)
            tryBox(ref.asExpr(ctx.addressSort), expr.type)
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
            tryBox(memory.allocateArray(expr.type.underlying(), ctx.sizeSort, len), expr.type)
        }
    }

    override fun visitGoSliceExpr(expr: GoSliceExpr): UExpr<out USort> {
        val boxedArray = expr.array.accept(this).asExpr(ctx.addressSort)
        val array = unboxNamedRef(boxedArray, expr.array.type).let { if (isPointerConcrete(it)) deref(it, ctx.addressSort) else it }
        val arrayType = expr.array.type.let { if (it is PointerType) it.baseType else it }.underlying()
        val sliceType = expr.type.underlying()
        val length = scope.calcOnState { memory.readArrayLength(array, arrayType, ctx.sizeSort) }
        val low = expr.low.accept(this).asExpr(ctx.sizeSort)
        val high = expr.high.accept(this).let { if (it is KBitVec32Value && it.intValue == -1) length else it }.asExpr(ctx.sizeSort)
        val count = ctx.mkSizeSubExpr(high, low)
        val elementType = when (arrayType) {
            is ArrayType -> arrayType.elementType
            is SliceType -> arrayType.elementType
            GoBasicTypes.STRING -> GoBasicTypes.UINT8
            else -> throw IllegalStateException("illegal type for collection")
        }
        val elementSort = ctx.typeToSort(elementType)

        checkLength(length) ?: throw IllegalStateException()
        checkNotNull(array) ?: throw IllegalStateException()
        checkNegativeIndex(low) ?: throw IllegalStateException()
        checkNegativeIndex(high) ?: throw IllegalStateException()
        checkIndexOutOfBounds(low, ctx.mkSizeAddExpr(high, ctx.mkSizeExpr(1))) ?: throw IllegalStateException()
        checkIndexOutOfBounds(high, ctx.mkSizeAddExpr(length, ctx.mkSizeExpr(1))) ?: throw IllegalStateException()

        return scope.calcOnState {
            val result = memory.allocateArray(sliceType, ctx.sizeSort, count)
            when (arrayType) {
                is ArrayType -> {
                    val tempArray = memory.allocateArray(sliceType, ctx.sizeSort, count)
                    for (i in 0 until arrayType.len) {
                        val idx = ctx.mkSizeExpr(i.toInt())
                        val element = memory.readArrayIndex(array, idx, arrayType, elementSort)
                        memory.writeArrayIndex(tempArray, idx, sliceType, elementSort, element, ctx.trueExpr)
                    }
                    memory.memcpy(tempArray, result, sliceType, elementSort, low, ctx.mkSizeExpr(0), count)
                }

                else -> memory.memcpy(array, result, sliceType, elementSort, low, ctx.mkSizeExpr(0), count)
            }
            tryBox(result, expr.type)
        }
    }

    override fun visitGoFieldAddrExpr(expr: GoFieldAddrExpr): UExpr<out USort> {
        if (expr.instance is GoNullConstant) {
            return scope.calcOnState {
                panic("nil struct")
                ctx.noValue
            }
        }

        val pointer = expr.instance.accept(this).asExpr(ctx.addressSort)
        val struct = deref(pointer, ctx.addressSort)
        checkNotNull(struct) ?: throw IllegalStateException()

            val fieldType = (expr.type as PointerType).baseType
            val fieldLValue = UFieldLValue(ctx.typeToSort(fieldType), struct, expr.field)
            return mkPointer(fieldType, fieldLValue)
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
        val arrayType = expr.instance.type.let { if (it is PointerType) it.baseType else it }.underlying()

        val elementType = (expr.type as PointerType).baseType
        val elementLValue = UArrayIndexLValue(ctx.typeToSort(elementType), array, index, arrayType)
        return mkPointer(elementType, elementLValue)
    }

    override fun visitGoIndexExpr(expr: GoIndexExpr): UExpr<out USort> {
        val (array, index) = visitIndexExpr(expr.instance, expr.index)
        val arrayType = expr.instance.type.let { if (it is PointerType) it.baseType else it }.underlying()
        return scope.calcOnState {
            memory.readArrayIndex(array, index, arrayType, ctx.typeToSort(expr.type))
        }
    }

    override fun visitGoLookupExpr(expr: GoLookupExpr): UExpr<out USort> {
        val map = unboxNamedRef(expr.instance.accept(this).asExpr(ctx.addressSort), expr.instance.type)
        val mapType = expr.instance.type.underlying() as MapType
        val key = expr.index.accept(this)

        val isRefKey = key.sort == ctx.addressSort
        val commaOk = expr.commaOk
        val valueSort = ctx.typeToSort(mapType.valueType)

        checkNotNull(map) ?: throw IllegalStateException()
        scope.ensureObjectMapSizeCorrect(map, mapType) ?: throw IllegalStateException()

        val contains = scope.calcOnState {
            if (isRefKey) {
                memory.refSetContainsElement(map, key.asExpr(ctx.addressSort), mapType)
            } else {
                memory.setContainsElement(map, key, mapType, USizeExprKeyInfo())
            }
        }
        val lvalue = if (isRefKey) {
            URefMapEntryLValue(valueSort, map, key.asExpr(ctx.addressSort), mapType)
        } else {
            UMapEntryLValue(key.sort, valueSort, map, key.asExpr(key.sort), mapType, USizeExprKeyInfo())
        }
        val rvalue = scope.calcOnState { memory.read(lvalue).asExpr(valueSort) }

        return scope.calcOnState {
            if (commaOk) {
                mkTuple(TupleType(listOf(mapType.valueType, GoBasicTypes.BOOL)), rvalue, contains)
            } else {
                ctx.mkIte(contains, { rvalue }, { rvalue.sort.sampleUValue() })
            }
        }
    }

    override fun visitGoSelectExpr(expr: GoSelectExpr): UExpr<out USort> {
        // channels aren't supported now
        return unsupportedExpr("Select")
    }

    override fun visitGoRangeExpr(expr: GoRangeExpr): UExpr<out USort> {
        val collection = expr.instance.accept(this).asExpr(ctx.addressSort).let {
            when (val type = expr.instance.type.underlying()) {
                is MapType -> copyMap(it, type)
                GoBasicTypes.STRING -> it
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
            when (val collectionType = tupleType.types[0].underlying()) {
                GoBasicTypes.STRING -> {
                    val index = memory.readField(iter, 1, ctx.sizeSort)
                    val char = memory.readArrayIndex(collection, index, collectionType, ctx.bv8Sort)
                    val length = memory.readArrayLength(collection, collectionType, ctx.sizeSort)
                    val ok = ctx.mkAnd(notNull, ctx.mkBvSignedLessExpr(index, length))

                    checkLength(length) ?: throw IllegalStateException()

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
                        k to v
                    } else {
                        val k = symbolicObjectMapAnyKey(collection, collectionType)
                        val v = symbolicObjectMapGet(collection, k, collectionType, ctx.typeToSort(collectionType.valueType))
                        symbolicObjectMapRemove(collection, k, collectionType)
                        k to v
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
        val unboxedValue = unbox(xAddr, assertSort)

        return scope.calcOnState {
            val sample = if (assertSort == ctx.addressSort) ctx.nullRef else assertSort.sampleUValue().asExpr(assertSort)
            if (commaOk) {
                mkTuple(tupleType, ite(xAddr, unboxedValue, sample), ite(xAddr, ctx.trueExpr, ctx.falseExpr))
            } else {
                ite(xAddr, unboxedValue, sample)
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
        return tryBox(mkBool(value.value), value.type)
    }

    override fun visitGoInt(value: GoInt): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value.toInt()), value.type)
    }

    override fun visitGoInt8(value: GoInt8): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value), value.type)
    }

    override fun visitGoInt16(value: GoInt16): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value), value.type)
    }

    override fun visitGoInt32(value: GoInt32): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value), value.type)
    }

    override fun visitGoInt64(value: GoInt64): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value), value.type)
    }

    override fun visitGoUInt(value: GoUInt): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value.toInt()), value.type)
    }

    override fun visitGoUInt8(value: GoUInt8): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value.toByte()), value.type)
    }

    override fun visitGoUInt16(value: GoUInt16): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value.toShort()), value.type)
    }

    override fun visitGoUInt32(value: GoUInt32): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value.toInt()), value.type)
    }

    override fun visitGoUInt64(value: GoUInt64): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value.toLong()), value.type)
    }

    override fun visitGoFloat32(value: GoFloat32): UExpr<out USort> = with(ctx) {
        return tryBox(mkFp(value.value, fp32Sort), value.type)
    }

    override fun visitGoFloat64(value: GoFloat64): UExpr<out USort> = with(ctx) {
        return tryBox(mkFp(value.value, fp64Sort), value.type)
    }

    override fun visitGoNullConstant(value: GoNullConstant): UExpr<out USort> = ctx.nullRef

    override fun visitGoStringConstant(value: GoStringConstant): UExpr<out USort> {
        return scope.calcOnState {
            tryBox(mkString(value.value), value.type)
        }
    }

    fun checkNotNull(obj: UHeapRef): Unit? = with(ctx) {
        scope.fork(mkHeapRefEq(obj, nullRef).not(), blockOnFalseState = {
            panic("null")
        })
    }

    fun unboxNamedRef(expr: UHeapRef, type: GoType): UHeapRef {
        if (type !is NamedType) {
            return expr
        }

        return unbox(expr, ctx.typeToSort(type.underlying())).asExpr(ctx.addressSort)
    }

    private fun visitGoBinaryExpr(expr: GoBinaryExpr): UExpr<out USort> {
        if (expr.lhv.type.underlying() == GoBasicTypes.STRING && expr.rhv.type.underlying() == GoBasicTypes.STRING) {
            return tryBox(appendArray(expr.lhv, expr.rhv), expr.type)
        }

        val lhv = expr.lhv.accept(this)
        val rhv = expr.rhv.accept(this)

        val signed = expr.lhv.type.underlying() is BasicType && !expr.lhv.type.underlying().typeName.startsWith("ui")
        val lhs = unboxNamedPrimitive(lhv, expr.lhv.type)
        val rhs = unboxNamedPrimitive(rhv, expr.rhv.type)
        val result = when (expr) {
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
        }(lhs, normalize(lhs, rhs, expr, signed))

        if (expr.type is NamedType) {
            return box(result, expr.type)
        }
        return result
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
        val array = unboxNamedRef(instance.accept(this).asExpr(ctx.addressSort), instance.type).let { if (isPointerConcrete(it)) deref(it, ctx.addressSort) else it }
        val arrayType = instance.type.let { if (it is PointerType) it.baseType else it }.underlying()
        val index = bv(unboxNamedPrimitive(idx.accept(this), idx.type)).mkNarrow(Int.SIZE_BITS, false).asExpr(ctx.sizeSort)
        val length = scope.calcOnState { memory.readArrayLength(array, arrayType, ctx.sizeSort) }

        checkLength(length) ?: throw IllegalStateException()
        checkNotNull(array) ?: throw IllegalStateException()
        checkNegativeIndex(index) ?: throw IllegalStateException()
        checkIndexOutOfBounds(index, length) ?: throw IllegalStateException()

        return array to index
    }

    private fun normalize(lhs: UExpr<out USort>, rhs: UExpr<out USort>, goExpr: GoBinaryExpr, signed: Boolean): UExpr<out USort> = with(ctx) {
        when (goExpr) {
            is GoShrExpr -> if (bv(lhs).sort.sizeBits != bv(rhs).sort.sizeBits) bv(rhs).mkNarrow(Long.SIZE_BITS, signed).asExpr(bv64Sort) else rhs
            else -> rhs
        }
    }

    private fun bv(expr: UExpr<out USort>): UExpr<UBvSort> {
        return expr.asExpr(expr.sort as UBvSort)
    }

    private fun isPointer(pointer: UHeapRef): UExpr<UBoolSort> {
        return scope.calcOnState { isPointer(pointer) }
    }

    private fun isPointerConcrete(pointer: UHeapRef): Boolean {
        if (pointer is UNullRef) {
            return false
        }
        return scope.calcOnState {
            val index = 1
            val field = memory.readField(pointer, index, ctx.bv32Sort)
            field is KBitVec32Value && field.intValue == POINTER_FIELD
        }
    }

    private fun isBoxed(ref: UHeapRef): UExpr<UBoolSort> {
        return scope.calcOnState { isBoxed(ref) }
    }

    private fun mkPointer(type: GoType): UConcreteHeapRef {
        return scope.calcOnState { mkPointer(type) }
    }

    private fun mkPointer(type: GoType, lvalue: ULValue<*, *>): UExpr<out USort> {
        return scope.calcOnState { mkPointer(type, lvalue) }
    }

    private fun <Sort : USort> deref(expr: UExpr<out USort>, sort: Sort): UExpr<Sort> = with(ctx) {
        val pointer = expr.asExpr(addressSort)
        checkIsPointer(pointer) ?: throw IllegalStateException()
        checkNotNull(pointer) ?: throw IllegalStateException()
        return scope.calcOnState {
            deref(pointer, sort).asExpr(sort)
        }
    }

    private fun box(expr: UExpr<out USort>, targetType: GoType): UHeapRef {
        return scope.calcOnState {
            box(expr, targetType)
        }
    }

    private fun unbox(expr: UHeapRef, sort: USort): UExpr<out USort> {
        checkIsBoxed(expr) ?: throw IllegalStateException()
        checkNotNull(expr) ?: throw IllegalStateException()
        return scope.calcOnState {
            unbox(expr, sort)
        }
    }

    private fun unboxNamedPrimitive(expr: UExpr<out USort>, type: GoType): UExpr<out USort> {
        if (type !is NamedType) {
            return expr
        }

        return unbox(expr.asExpr(ctx.addressSort), ctx.typeToSort(type.underlying()))
    }

    private fun tryBox(expr: UExpr<out USort>, targetType: GoType): UExpr<out USort> {
        return if (targetType is NamedType) box(expr, targetType) else expr
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
        scope.assert(mkSizeGeExpr(length, mkSizeExpr(0)))
    }

    private fun checkSliceToArrayPointerLength(
        sliceLength: UExpr<USizeSort>,
        arrayLength: UExpr<USizeSort>
    ): Unit? = with(ctx) {
        scope.fork(mkSizeGeExpr(sliceLength, arrayLength), blockOnFalseState = {
            panic("length of the slice is less than the length of the array")
        })
    }

    private fun checkIsPointer(obj: UHeapRef): Unit? = scope.fork(isPointer(obj), blockOnFalseState = {
        panic("not a pointer")
    })

    private fun checkIsBoxed(obj: UHeapRef): Unit? = scope.fork(isBoxed(obj), blockOnFalseState = {
        panic("not a boxed value")
    })

    private fun callBuiltin(method: GoBuiltin, args: List<GoValue>, returnType: GoType): UExpr<out USort> {
        return when (method.name) {
            "append" -> tryBox(appendArray(args[0], args[1]), returnType)
            "copy" -> {
                val src = unboxNamedRef(args[1].accept(this).asExpr(ctx.addressSort), args[1].type)
                val dst = unboxNamedRef(args[0].accept(this).asExpr(ctx.addressSort), args[0].type)
                val sliceType = args[0].type.underlying() as SliceType
                val elementSort = ctx.typeToSort(sliceType.elementType)

                return scope.calcOnState {
                    val srcLength = memory.readArrayLength(src, sliceType, ctx.sizeSort)
                    val dstLength = memory.readArrayLength(dst, sliceType, ctx.sizeSort)
                    checkLength(srcLength) ?: throw IllegalStateException()
                    checkLength(dstLength) ?: throw IllegalStateException()
                    val numCopied = ctx.mkIte(ctx.mkSizeLtExpr(srcLength, dstLength), srcLength, dstLength)

                    memory.memcpy(src, dst, sliceType, elementSort, ctx.mkSizeExpr(0), ctx.mkSizeExpr(0), numCopied)
                    numCopied
                }
            }

            "delete" -> {
                val map = unboxNamedRef(args[0].accept(this).asExpr(ctx.addressSort), args[0].type)
                val key = args[1].accept(this)
                val mapType = args[0].type.underlying() as MapType
                val keySort = ctx.typeToSort(mapType.keyType)

                scope.ensureObjectMapSizeCorrect(map, mapType) ?: throw IllegalStateException()
                scope.doWithState {
                    if (keySort == ctx.addressSort) {
                        symbolicObjectMapRemove(map, key.asExpr(ctx.addressSort), mapType)
                    } else {
                        symbolicPrimitiveMapRemove(map, key.asExpr(keySort), mapType, USizeExprKeyInfo())
                    }
                }
                return ctx.voidValue
            }

            "len", "cap" -> {
                val arg = args[0]
                val collection = unboxNamedRef(arg.accept(this).asExpr(ctx.addressSort), arg.type)

                return ctx.mkIte(
                    ctx.mkNot(ctx.mkHeapRefEq(collection, ctx.nullRef)),
                    trueBranch = {
                        scope.calcOnState {
                            when (val type = arg.type.underlying()) {
                                is ArrayType, is SliceType, is BasicType -> {
                                    val length = memory.readArrayLength(collection, type, ctx.sizeSort)
                                    checkLength(length) ?: throw IllegalStateException()
                                    length
                                }

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

    private fun appendArray(sliceValue: GoValue, appendValue: GoValue): UExpr<out USort> {
        val slice = unboxNamedRef(sliceValue.accept(this).asExpr(ctx.addressSort), sliceValue.type)
        val append = unboxNamedRef(appendValue.accept(this).asExpr(ctx.addressSort), appendValue.type)
        val sliceType = sliceValue.type.underlying()

        val elementSort = if (sliceType == GoBasicTypes.STRING) ctx.bv8Sort else ctx.typeToSort((sliceType as SliceType).elementType)
        val zero = ctx.mkSizeExpr(0)

        return scope.calcOnState {
            val sliceLength = ctx.mkIte(
                ctx.mkHeapRefEq(slice, ctx.nullRef),
                { zero },
                { memory.readArrayLength(slice, sliceType, ctx.sizeSort) }
            )
            val appendLength = ctx.mkIte(
                ctx.mkHeapRefEq(append, ctx.nullRef),
                { zero },
                { memory.readArrayLength(append, sliceType, ctx.sizeSort) }
            )
            checkLength(sliceLength) ?: throw IllegalStateException()
            checkLength(appendLength) ?: throw IllegalStateException()

            val length = ctx.mkSizeAddExpr(sliceLength, appendLength)
            val out = memory.allocateArray(sliceType, ctx.sizeSort, length)

            memory.memcpy(slice, out, sliceType, elementSort, zero, zero, sliceLength)
            memory.memcpy(append, out, sliceType, elementSort, zero, sliceLength, appendLength)

            out
        }
    }

    private fun changeType(value: UExpr<out USort>, baseType: GoType, targetType: GoType): UExpr<out USort> {
        return when (targetType) {
            is NamedType -> {
                when (baseType) {
                    is NamedType -> box(unbox(value.asExpr(ctx.addressSort), ctx.typeToSort(baseType)), targetType)
                    else -> box(value, targetType)
                }
            }

            is PointerType -> {
                ctx.mkIte(
                    ctx.mkHeapRefEq(value.asExpr(ctx.addressSort), ctx.nullRef),
                    trueBranch = { ctx.nullRef },
                    falseBranch = {
                        val basePointerType = (baseType as PointerType).baseType
                        val baseValue = deref(value.asExpr(ctx.addressSort), ctx.typeToSort(basePointerType))
                        val targetPointerType = targetType.baseType
                        val targetValue = changeType(baseValue, basePointerType, targetPointerType)
                        scope.calcOnState { mkPointer(targetPointerType, targetValue) }
                    }
                )
            }

            else -> {
                unbox(value.asExpr(ctx.addressSort), ctx.typeToSort(targetType))
            }
        }
    }

    private fun mockCall(expr: GoCallExpr, func: GoValue): UExpr<out USort> {
        val funcName = when(func) {
            is GoParameter -> func.name
            is GoFunction  -> func.name
            else -> "unnamed"
        }
        val signature = func.type as SignatureType
        val returnType = when (signature.results.types.size) {
            0 -> NullType()
            1 -> signature.results.types[0]
            else -> signature.results
        }
        val method = GoFunction(signature, emptyList(), funcName, emptyList(), "", emptyList(), emptyList())
        val mockSort = ctx.typeToSort(returnType)
        val mockValue = scope.calcOnState {
            memory.mocker.call(method, expr.args.map { it.accept(this@GoExprVisitor) }.asSequence(), mockSort, memory.ownership)
        }
        if (mockSort == ctx.addressSort) {
            val constraint = scope.calcOnState {
                memory.types.evalIsSubtype(mockValue.asExpr(ctx.addressSort), returnType)
            }
            scope.assert(constraint)
        }
        return mockValue
    }

    private fun unsupportedExpr(name: String): UExpr<out USort> {
        throw UnsupportedOperationException("Expression '$name' not supported")
    }
}