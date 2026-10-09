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
import org.usvm.api.collection.ObjectMapCollectionApi.ensureObjectMapSizeCorrect
import org.usvm.api.collection.ObjectMapCollectionApi.mkSymbolicObjectMap
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapAnyKey
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapGet
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapMergeInto
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapRemove
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapSize
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapAnyKey
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapCopyIntoEmpty
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapGet
import org.usvm.api.collection.PrimitiveMapCollectionApi.symbolicPrimitiveMapRemove
import org.usvm.api.readField
import org.usvm.api.refSetContainsElement
import org.usvm.api.setContainsElement
import org.usvm.api.typeStreamOf
import org.usvm.api.writeField
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.interpreter.GoStepAbort
import org.usvm.interpreter.GoStepScope
import org.usvm.memory.GoArrayView
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.allocateGoArray
import org.usvm.memory.arrayStorageType
import org.usvm.memory.arrayView
import org.usvm.memory.copyGoArray
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.memory.readGoArrayIndex
import org.usvm.memory.readGoArrayLength
import org.usvm.operator.GoBinaryOperator
import org.usvm.operator.GoUnaryOperator
import org.usvm.operator.mkNarrow
import org.usvm.state.GoMethodResult
import org.usvm.state.GoState.Companion.POINTER_FIELD
import org.usvm.statistics.ApplicationGraph
import org.usvm.type.GoBasicTypes
import org.usvm.type.underlying
import org.usvm.types.first
import org.usvm.util.hasUnsupportedInstructions
import org.usvm.util.isInit
import java.util.Base64

@Suppress("LargeClass") // One visitor implements the SSA expression interface.
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
                    scope.assert(memory.types.evalIsSubtype(instance, func.type)) ?: throw GoStepAbort()
                    memory.typeStreamOf(instance).first()
                }
                program.findMethod(expr.location, "(${type.typeName}).${checkNotNull(expr.callee).name}")
            }

            func is GoFunction -> {
                program.findMethod(expr.location, func.metName)
            }
            func is GoVar -> {
                scope.calcOnState {
                    program.findMethod(
                        expr.location,
                        (memory.read(URegisterStackLValue(ctx.addressSort, index(func.name))) as KConst).decl.name,
                    )
                }
            }

            else -> {
                throw UnknownFunctionException(func.toString())
            }
        }
        if (method.hasUnsupportedInstructions()) {
            return unsupportedExpr("method ${method.metName} contains unsupported instructions")
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
                node = checkNotNull(node.parent) { "Phi has no predecessor in the execution path" }
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
        val sourceType = expr.operand.type.underlying()
        val targetType = expr.type.underlying()
        if (sourceType == GoBasicTypes.STRING || targetType == GoBasicTypes.STRING) {
            val sliceType = (if (sourceType == GoBasicTypes.STRING) targetType else sourceType) as? SliceType
            if (sliceType?.elementType != GoBasicTypes.UINT8) {
                return unsupportedExpr("string conversions other than []byte")
            }
            val source = unboxNamedRef(value.asExpr(ctx.addressSort), expr.operand.type)
            return scope.calcOnState {
                val view = arrayView(source, sourceType)
                val destination = memory.allocateGoArray(targetType, ctx.sizeSort, view.length)
                memory.copyGoArray(
                    view.backing,
                    destination,
                    view.storageType,
                    ctx.bv8Sort,
                    view.offset,
                    ctx.mkSizeExpr(0),
                    view.length
                )
                tryBox(destination, expr.type)
            }
        }
        val unsafeConversion = sourceType == GoBasicTypes.UNSAFE_POINTER || targetType == GoBasicTypes.UNSAFE_POINTER
        if (unsafeConversion || targetType is PointerType) {
            return unsupportedExpr("unsafe pointer conversion")
        }
        val operand = unboxNamedPrimitive(value, expr.operand.type)
        val targetSort = ctx.typeToSort(targetType)
        val converted = if (operand.sort is UBvSort && targetSort is UBvSort) {
            val signed = !(sourceType as BasicType).typeName.startsWith("uint")
            bv(operand).mkNarrow(targetSort.sizeBits.toInt(), signed = signed)
        } else {
            ctx.mkPrimitiveCast(operand, targetSort)
        }
        return tryBox(converted, expr.type)
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
        val view = scope.calcOnState { arrayView(slice, sliceType) }
        val arrayType = (expr.type as PointerType).baseType.underlying() as ArrayType
        val arrayLength = ctx.mkSizeExpr(arrayType.len.toInt())
        checkSliceToArrayPointerLength(view.length, arrayLength) ?: throw GoStepAbort()

        return scope.calcOnState {
            val array = memory.allocateGoArray(arrayType, ctx.sizeSort, arrayLength)
            data.arrayViews[array] = view.copy(length = arrayLength, capacity = arrayLength)
            tryBox(mkPointer(arrayType, array), expr.type)
        }
    }

    override fun visitGoMakeInterfaceExpr(expr: GoMakeInterfaceExpr): UExpr<out USort> {
        val value = box(expr.value.accept(this@GoExprVisitor), expr.value.type)
        scope.doWithState {
            scope.assert(memory.types.evalIsSubtype(value, expr.type)) ?: throw GoStepAbort()
        }

        return value
    }

    override fun visitGoMakeClosureExpr(expr: GoMakeClosureExpr): UExpr<out USort> {
        return ctx.mkConst(expr.func.name, ctx.addressSort)
    }

    override fun visitGoMakeMapExpr(expr: GoMakeMapExpr): UExpr<out USort> {
        val mapType = expr.type.underlying()


        return scope.calcOnState {
            val ref = memory.allocConcrete(mapType)
            memory.write(UMapLengthLValue(ref, mapType, ctx.sizeSort), ctx.mkSizeExpr(0), ctx.trueExpr)
            tryBox(ref.asExpr(ctx.addressSort), expr.type)
        }
    }

    override fun visitGoMakeChanExpr(expr: GoMakeChanExpr): UExpr<out USort> {
        // channels aren't supported now
        return unsupportedExpr("MakeChan")
    }

    override fun visitGoMakeSliceExpr(expr: GoMakeSliceExpr): UExpr<out USort> {
        val length = collectionSize(expr.len)
        val capacity = collectionSize(expr.cap)

        checkLength(length) ?: throw GoStepAbort()
        checkLength(capacity) ?: throw GoStepAbort()
        checkIndexOutOfBounds(length, ctx.mkSizeAddExpr(capacity, ctx.mkSizeExpr(1))) ?: throw GoStepAbort()

        return scope.calcOnState {
            val type = expr.type.underlying()
            val backing = memory.allocateGoArray(type, ctx.sizeSort, capacity)
            val slice = memory.allocateGoArray(type, ctx.sizeSort, length)
            data.arrayViews[slice] = GoArrayView(
                backing, type.arrayStorageType(), offset = ctx.mkSizeExpr(0), length = length, capacity = capacity,
            )
            tryBox(slice, expr.type)
        }
    }

    override fun visitGoSliceExpr(expr: GoSliceExpr): UExpr<out USort> {
        val boxedArray = expr.array.accept(this).asExpr(ctx.addressSort)
        val array = unboxNamedRef(boxedArray, expr.array.type).let {
            if (isPointerConcrete(it)) deref(it, ctx.addressSort) else it
        }
        val arrayType = expr.array.type.let { if (it is PointerType) it.baseType else it }.underlying()
        val view = scope.calcOnState { arrayView(array, arrayType) }
        val low = collectionSize(expr.low)
        val high = sliceBound(expr.high, view.length)
        val limit = sliceBound(expr.max, view.capacity)
        val count = ctx.mkSizeSubExpr(high, low)

        checkLength(view.length) ?: throw GoStepAbort()
        checkNegativeIndex(low) ?: throw GoStepAbort()
        checkNegativeIndex(high) ?: throw GoStepAbort()
        checkNegativeIndex(limit) ?: throw GoStepAbort()
        checkIndexOutOfBounds(low, ctx.mkSizeAddExpr(high, ctx.mkSizeExpr(1))) ?: throw GoStepAbort()
        checkIndexOutOfBounds(high, ctx.mkSizeAddExpr(limit, ctx.mkSizeExpr(1))) ?: throw GoStepAbort()
        checkIndexOutOfBounds(limit, ctx.mkSizeAddExpr(view.capacity, ctx.mkSizeExpr(1))) ?: throw GoStepAbort()

        val result = scope.calcOnState {
            val reference = memory.allocateGoArray(expr.type.underlying(), ctx.sizeSort, count)
            data.arrayViews[reference] = view.copy(
                offset = ctx.mkSizeAddExpr(view.offset, low),
                length = count,
                capacity = ctx.mkSizeSubExpr(limit, low),
            )
            reference
        }
        val slice = ctx.mkIte(ctx.mkHeapRefEq(array, ctx.nullRef), ctx.nullRef, result)
        return tryBox(slice, expr.type)
    }

    private fun sliceBound(value: GoValue, default: UExpr<USizeSort>): UExpr<USizeSort> {
        return if (value is GoNullConstant) default else collectionSize(value)
    }

    private fun collectionSize(value: GoValue): UExpr<USizeSort> {
        val original = bv(unboxNamedPrimitive(value.accept(this), value.type))
        val unsigned = (value.type.underlying() as BasicType).typeName.startsWith("uint")
        if (!unsigned) {
            scope.fork(ctx.mkBvSignedGreaterOrEqualExpr(original, ctx.mkBv(0, original.sort)), blockOnFalseState = {
                panic("negative length or slice bound")
            }) ?: throw GoStepAbort()
        }
        // Collection memory uses BV32 sizes; restrict symbolic sizes to its representable domain.
        if (original.sort.sizeBits > Int.SIZE_BITS.toUInt()) {
            scope.assert(ctx.mkBvUnsignedLessOrEqualExpr(original, ctx.mkBv(Int.MAX_VALUE.toLong(), original.sort)))
                ?: throw GoStepAbort()
        }
        return original.mkNarrow(Int.SIZE_BITS, signed = false).asExpr(ctx.sizeSort)
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
        checkNotNull(struct) ?: throw GoStepAbort()

        val fieldType = (expr.type as PointerType).baseType
        val fieldLValue = UFieldLValue(ctx.typeToSort(fieldType), struct, expr.field)
        return mkPointer(fieldType, fieldLValue)
    }

    override fun visitGoFieldExpr(expr: GoFieldExpr): UExpr<out USort> {
        val struct = expr.instance.accept(this).asExpr(ctx.addressSort)
        checkNotNull(struct) ?: throw GoStepAbort()
        return scope.calcOnState {
            memory.readField(struct, expr.field, ctx.typeToSort(expr.type))
        }
    }

    override fun visitGoIndexAddrExpr(expr: GoIndexAddrExpr): UExpr<out USort> {
        val (view, index) = visitIndexExpr(expr.instance, expr.index)

        val elementType = (expr.type as PointerType).baseType
        val elementLValue = UArrayIndexLValue(ctx.typeToSort(elementType), view.backing, index, view.storageType)
        return mkPointer(elementType, elementLValue)
    }

    override fun visitGoIndexExpr(expr: GoIndexExpr): UExpr<out USort> {
        val (view, index) = visitIndexExpr(expr.instance, expr.index)
        return scope.calcOnState {
            memory.readGoArrayIndex(view.backing, index, view.storageType, ctx.typeToSort(expr.type))
        }
    }

    override fun visitGoLookupExpr(expr: GoLookupExpr): UExpr<out USort> {
        val map = unboxNamedRef(expr.instance.accept(this).asExpr(ctx.addressSort), expr.instance.type)
        val mapType = expr.instance.type.underlying() as MapType
        val key = expr.index.accept(this)

        val isRefKey = key.sort == ctx.addressSort
        val commaOk = expr.commaOk
        val valueSort = ctx.typeToSort(mapType.valueType)

        checkNotNull(map) ?: throw GoStepAbort()
        scope.ensureObjectMapSizeCorrect(map, mapType) ?: throw GoStepAbort()

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
                is MapType -> {
                    copyMap(it, type)
                }
                GoBasicTypes.STRING -> {
                    it
                }
                else -> {
                    error("illegal type for range")
                }
            }
        }
        return scope.calcOnState {
            mkTuple(
                TupleType(listOf(expr.instance.type, GoBasicTypes.INT32)),
                collection,
                ctx.mkSizeExpr(0)
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
                    val char = memory.readGoArrayIndex(collection, index, collectionType, ctx.bv8Sort)
                    val length = memory.readGoArrayLength(collection, collectionType, ctx.sizeSort)
                    val ok = ctx.mkAnd(notNull, ctx.mkBvSignedLessExpr(index, length))

                    checkLength(length) ?: throw GoStepAbort()

                    memory.writeField(iter, 1, ctx.sizeSort, ctx.mkBvAddExpr(index, ctx.mkSizeExpr(1)), ctx.trueExpr)
                    mkTuple(
                        TupleType(listOf(GoBasicTypes.BOOL, GoBasicTypes.INT32, GoBasicTypes.INT32)),
                        ok,
                        index,
                        ctx.mkPrimitiveCast(char, ctx.bv32Sort)
                    )
                }

                is MapType -> {
                    scope.ensureObjectMapSizeCorrect(collection, collectionType) ?: throw GoStepAbort()

                    val length = symbolicObjectMapSize(collection, collectionType)
                    val ok = ctx.mkAnd(notNull, ctx.mkBvSignedGreaterExpr(length, ctx.mkBv(0)))
                    val isPrimitiveKey = collectionType.keyType.underlying() is BasicType
                    val (key, value) = if (isPrimitiveKey) {
                        val k =
                            symbolicPrimitiveMapAnyKey(
                                collection,
                                collectionType,
                                ctx.typeToSort(collectionType.keyType),
                                USizeExprKeyInfo()
                            )
                        val v =
                            symbolicPrimitiveMapGet(
                                collection,
                                k,
                                collectionType,
                                ctx.typeToSort(collectionType.valueType),
                                USizeExprKeyInfo()
                            )
                        symbolicPrimitiveMapRemove(collection, k, collectionType, USizeExprKeyInfo())
                        k to v
                    } else {
                        val k = symbolicObjectMapAnyKey(collection, collectionType)
                        val v =
                            symbolicObjectMapGet(
                                collection,
                                k,
                                collectionType,
                                ctx.typeToSort(collectionType.valueType)
                            )
                        symbolicObjectMapRemove(collection, k, collectionType)
                        k to v
                    }

                    mkTuple(
                        TupleType(listOf(GoBasicTypes.BOOL, collectionType.keyType, collectionType.valueType)),
                        ok,
                        key,
                        value
                    )
                }

                else -> {
                    error("invalid collection type in next expr")
                }
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
        checkNotNull(xAddr) ?: throw GoStepAbort()
        val unboxedValue = unbox(xAddr, assertSort)

        return scope.calcOnState {
            val sample = if (assertSort == ctx.addressSort) {
                ctx.nullRef
            } else {
                assertSort.sampleUValue().asExpr(
                    assertSort
                )
            }
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
            memory.read(
                URegisterStackLValue(ctx.typeToSort(expr.type), expr.index + ctx.freeVariableOffset(lastEnteredMethod))
            )
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
        return tryBox(mkBv(value.value.toLong(), typeToSort(value.type.underlying()) as UBvSort), value.type)
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
        return tryBox(mkBv(value.value.toLong(), typeToSort(value.type.underlying()) as UBvSort), value.type)
    }

    override fun visitGoUInt8(value: GoUInt8): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value.toByte()), value.type)
    }

    override fun visitGoUInt16(value: GoUInt16): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value.toShort()), value.type)
    }

    override fun visitGoUInt32(value: GoUInt32): UExpr<out USort> = with(ctx) {
        return tryBox(mkBv(value.value.toLong(), typeToSort(value.type.underlying()) as UBvSort), value.type)
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
            tryBox(mkString(Base64.getDecoder().decode(value.value)), value.type)
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

    @Suppress("ThrowsCount") // Failed scope checks stop this hop.
    private fun visitGoBinaryExpr(expr: GoBinaryExpr): UExpr<out USort> {
        if (expr.lhv.type.underlying() == GoBasicTypes.STRING && expr.rhv.type.underlying() == GoBasicTypes.STRING) {
            return if (expr is GoAddExpr) {
                tryBox(appendArray(expr.lhv, expr.rhv), expr.type)
            } else {
                compareStrings(expr)
            }
        }

        val lhv = expr.lhv.accept(this)
        val rhv = expr.rhv.accept(this)

        val isEquality = expr is GoEqlExpr || expr is GoNeqExpr
        val hasNilOperand = expr.lhv is GoNullConstant || expr.rhv is GoNullConstant
        val bothReferences = lhv.sort == ctx.addressSort && rhv.sort == ctx.addressSort
        if (isEquality && hasNilOperand && bothReferences) {
            val equal = ctx.mkHeapRefEq(lhv.asExpr(ctx.addressSort), rhv.asExpr(ctx.addressSort))
            return if (expr is GoEqlExpr) equal else ctx.mkNot(equal)
        }

        val operandType = expr.lhv.type.underlying()
        val signed = operandType is BasicType && !operandType.typeName.startsWith("ui")
        val lhs = unboxNamedPrimitive(lhv, expr.lhv.type)
        val rhs = unboxNamedPrimitive(rhv, expr.rhv.type)
        if ((expr is GoDivExpr || expr is GoModExpr) && rhs.sort is UBvSort) {
            scope.fork(ctx.mkNot(ctx.mkEq(bv(rhs), ctx.mkBv(0, bv(rhs).sort))), blockOnFalseState = {
                panic("integer divide by zero")
            }) ?: throw GoStepAbort()
        }
        val isShift = expr is GoShrExpr || expr is GoShlExpr
        val signedShift = isShift && !(expr.rhv.type.underlying() as BasicType).typeName.startsWith("uint")
        if (signedShift) {
            val count = bv(rhs)
            scope.fork(ctx.mkBvSignedGreaterOrEqualExpr(count, ctx.mkBv(0, count.sort)), blockOnFalseState = {
                panic("negative shift amount")
            }) ?: throw GoStepAbort()
        }

        val result = when (expr) {
            is GoAddExpr -> {
                GoBinaryOperator.Add
            }
            is GoSubExpr -> {
                GoBinaryOperator.Sub
            }
            is GoMulExpr -> {
                GoBinaryOperator.Mul
            }
            is GoDivExpr -> {
                GoBinaryOperator.Div(signed)
            }
            is GoModExpr -> {
                GoBinaryOperator.Mod(signed)
            }
            is GoAndExpr -> {
                GoBinaryOperator.And
            }
            is GoOrExpr -> {
                GoBinaryOperator.Or
            }
            is GoXorExpr -> {
                GoBinaryOperator.Xor
            }
            is GoShlExpr -> {
                GoBinaryOperator.Shl
            }
            is GoShrExpr -> {
                GoBinaryOperator.Shr(signed)
            }
            is GoAndNotExpr -> {
                GoBinaryOperator.AndNot
            }
            is GoEqlExpr -> {
                GoBinaryOperator.Eql
            }
            is GoLssExpr -> {
                GoBinaryOperator.Lss(signed)
            }
            is GoGtrExpr -> {
                GoBinaryOperator.Gtr(signed)
            }
            is GoNeqExpr -> {
                GoBinaryOperator.Neq
            }
            is GoLeqExpr -> {
                GoBinaryOperator.Leq(signed)
            }
            is GoGeqExpr -> {
                GoBinaryOperator.Geq(signed)
            }
            else -> {
                throw UnknownBinaryOperationException(expr.toString())
            }
        }(lhs, normalize(lhs, rhs, expr))

        if (expr.type is NamedType) {
            return box(result, expr.type)
        }
        return result
    }

    private fun visitGoUnaryExpr(expr: GoUnaryExpr): UExpr<out USort> {
        val x = expr.value.accept(this)
        return when (expr) {
            is GoUnArrowExpr -> {
                throw UnsupportedUnaryOperationException("channel operations")
            }
            is GoUnXorExpr -> {
                GoUnaryOperator.Complement(x)
            }
            is GoUnNotExpr, is GoUnSubExpr -> {
                GoUnaryOperator.Neg(x)
            }
            is GoUnMulExpr -> {
                deref(x, ctx.typeToSort(expr.type))
            }
            else -> {
                throw UnknownUnaryOperationException(expr.toString())
            }
        }
    }

    private fun visitIndexExpr(instance: GoValue, idx: GoValue): Pair<GoArrayView, UExpr<USizeSort>> {
        val array = unboxNamedRef(instance.accept(this).asExpr(ctx.addressSort), instance.type).let {
            if (isPointerConcrete(it)) deref(it, ctx.addressSort) else it
        }
        val arrayType = instance.type.let { if (it is PointerType) it.baseType else it }.underlying()
        val originalIndex = bv(unboxNamedPrimitive(idx.accept(this), idx.type))
        val view = scope.calcOnState { arrayView(array, arrayType) }
        val width = maxOf(originalIndex.sort.sizeBits, view.length.sort.sizeBits).toInt()
        val fullIndex = originalIndex.mkNarrow(width, signed = false)
        val fullLength = bv(view.length).mkNarrow(width, signed = false)
        scope.fork(ctx.mkBvUnsignedLessExpr(fullIndex, fullLength), blockOnFalseState = {
            panic("index out of bounds")
        }) ?: throw GoStepAbort()
        val index = toSizeExpr(originalIndex)

        return view to ctx.mkSizeAddExpr(view.offset, index)
    }

    private fun normalize(lhs: UExpr<out USort>, rhs: UExpr<out USort>, goExpr: GoBinaryExpr): UExpr<out USort> = with(
        ctx
    ) {
        if (goExpr !is GoShrExpr && goExpr !is GoShlExpr) return rhs

        val valueSort = bv(lhs).sort
        val count = bv(rhs)
        val width = mkBv(valueSort.sizeBits.toLong(), count.sort)
        val tooLarge = mkBvUnsignedGreaterOrEqualExpr(count, width)
        val normalized = mkPrimitiveCast(count, valueSort).asExpr(valueSort)
        mkIte(tooLarge, mkBv(valueSort.sizeBits.toLong(), valueSort), normalized)
    }

    private fun toSizeExpr(expr: UExpr<out USort>): UExpr<USizeSort> =
        ctx.mkPrimitiveCast(expr, ctx.sizeSort).asExpr(ctx.sizeSort)

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
        checkIsPointer(pointer) ?: throw GoStepAbort()
        checkNotNull(pointer) ?: throw GoStepAbort()
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
        checkIsBoxed(expr) ?: throw GoStepAbort()
        checkNotNull(expr) ?: throw GoStepAbort()
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
        arrayLength: UExpr<USizeSort>,
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

    @Suppress("ThrowsCount") // Failed scope checks stop this hop.
    private fun callBuiltin(method: GoBuiltin, args: List<GoValue>, returnType: GoType): UExpr<out USort> {
        return when (method.name) {
            "append" -> {
                tryBox(appendArray(args[0], args[1]), returnType)
            }
            "copy" -> {
                val source = unboxNamedRef(args[1].accept(this).asExpr(ctx.addressSort), args[1].type)
                val destination = unboxNamedRef(args[0].accept(this).asExpr(ctx.addressSort), args[0].type)
                val sliceType = args[0].type.underlying() as SliceType
                val count = scope.calcOnState {
                    val sourceView = arrayView(source, args[1].type)
                    val destinationView = arrayView(destination, args[0].type)
                    val copied = ctx.mkIte(
                        ctx.mkSizeLtExpr(sourceView.length, destinationView.length),
                        sourceView.length,
                        destinationView.length
                    )
                    memory.copyGoArray(
                        sourceView.backing,
                        destinationView.backing,
                        sourceView.storageType,
                        ctx.typeToSort(sliceType.elementType),
                        sourceView.offset,
                        destinationView.offset,
                        copied
                    )
                    copied
                }
                ctx.mkPrimitiveCast(count, ctx.typeToSort(returnType))
            }

            "delete" -> {
                val map = unboxNamedRef(args[0].accept(this).asExpr(ctx.addressSort), args[0].type)
                val key = args[1].accept(this)
                val mapType = args[0].type.underlying() as MapType
                val keySort = ctx.typeToSort(mapType.keyType)

                scope.ensureObjectMapSizeCorrect(map, mapType) ?: throw GoStepAbort()
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

                return ctx.mkPrimitiveCast(
                    ctx.mkIte(
                        ctx.mkNot(ctx.mkHeapRefEq(collection, ctx.nullRef)),
                        trueBranch = {
                            scope.calcOnState {
                                when (val type = arg.type.underlying()) {
                                    is ArrayType, is SliceType, is BasicType -> {
                                        val view = arrayView(collection, type)
                                        val size = if (method.name == "cap") view.capacity else view.length
                                        checkLength(size) ?: throw GoStepAbort()
                                        size
                                    }

                                    is MapType -> {
                                        scope.ensureObjectMapSizeCorrect(collection, type) ?: throw GoStepAbort()
                                        symbolicObjectMapSize(collection, type)
                                    }

                                    else -> {
                                        error("Inconsistent Go interpreter state")
                                    }
                                }
                            }
                        },
                        falseBranch = {
                            ctx.mkSizeExpr(0)
                        },
                    ),
                    ctx.typeToSort(returnType)
                )
            }

            "panic" -> {
                val value = args[0].accept(this)
                scope.calcOnState {
                    panic(value, args[0].type)
                    ctx.noValue
                }
            }

            "recover" -> {
                scope.calcOnState {
                    recover()
                }
            }

            else -> {
                unsupportedExpr("builtin ${method.name}")
            }
        }
    }

    private fun copyMap(
        srcMap: UHeapRef,
        mapType: MapType,
    ): UHeapRef = with(ctx) {
        checkNotNull(srcMap) ?: throw GoStepAbort()
        scope.ensureObjectMapSizeCorrect(srcMap, mapType) ?: throw GoStepAbort()

        val keySort = typeToSort(mapType.keyType)
        val valueSort = typeToSort(mapType.valueType)
        val isRefSet = keySort == addressSort
        return scope.calcOnState {
            val destMap = mkSymbolicObjectMap(mapType)
            if (isRefSet) {
                symbolicObjectMapMergeInto(destMap, srcMap, mapType, valueSort)
            } else {
                symbolicPrimitiveMapCopyIntoEmpty(destMap, srcMap, mapType, keySort, valueSort, USizeExprKeyInfo())
            }
            destMap
        }
    }

    private fun compareStrings(expr: GoBinaryExpr): UExpr<UBoolSort> {
        val left = unboxNamedRef(expr.lhv.accept(this).asExpr(ctx.addressSort), expr.lhv.type)
        val right = unboxNamedRef(expr.rhv.accept(this).asExpr(ctx.addressSort), expr.rhv.type)
        return scope.calcOnState {
            val leftView = arrayView(left, GoBasicTypes.STRING)
            val rightView = arrayView(right, GoBasicTypes.STRING)
            val bound = (leftView.length as? KBitVec32Value)?.intValue
                ?: (rightView.length as? KBitVec32Value)?.intValue
                ?: throw UnsupportedOperationException("String comparison with two symbolic lengths is not supported")
            require(bound >= 0) { "Negative string length" }
            var equalPrefix: UExpr<UBoolSort> = ctx.trueExpr
            var less: UExpr<UBoolSort> = ctx.falseExpr
            for (index in 0 until bound) {
                val position = ctx.mkSizeExpr(index)
                val inBoth = ctx.mkAnd(
                    ctx.mkSizeLtExpr(position, leftView.length),
                    ctx.mkSizeLtExpr(position, rightView.length)
                )
                val leftByte = memory.readGoArrayIndex(
                    leftView.backing,
                    ctx.mkSizeAddExpr(leftView.offset, position),
                    leftView.storageType,
                    ctx.bv8Sort
                )
                val rightByte = memory.readGoArrayIndex(
                    rightView.backing,
                    ctx.mkSizeAddExpr(rightView.offset, position),
                    rightView.storageType,
                    ctx.bv8Sort
                )
                less = ctx.mkOr(less, ctx.mkAnd(equalPrefix, inBoth, ctx.mkBvUnsignedLessExpr(leftByte, rightByte)))
                equalPrefix = ctx.mkAnd(equalPrefix, ctx.mkOr(ctx.mkNot(inBoth), ctx.mkEq(leftByte, rightByte)))
            }
            less = ctx.mkOr(less, ctx.mkAnd(equalPrefix, ctx.mkSizeLtExpr(leftView.length, rightView.length)))
            val equal = ctx.mkAnd(equalPrefix, ctx.mkEq(leftView.length, rightView.length))
            when (expr) {
                is GoEqlExpr -> {
                    equal
                }
                is GoNeqExpr -> {
                    ctx.mkNot(equal)
                }
                is GoLssExpr -> {
                    less
                }
                is GoLeqExpr -> {
                    ctx.mkOr(less, equal)
                }
                is GoGtrExpr -> {
                    ctx.mkNot(ctx.mkOr(less, equal))
                }
                is GoGeqExpr -> {
                    ctx.mkNot(less)
                }
                else -> {
                    error("Unsupported string operation: $expr")
                }
            }
        }
    }

    private fun appendArray(sliceValue: GoValue, appendValue: GoValue): UExpr<out USort> {
        val slice = unboxNamedRef(sliceValue.accept(this).asExpr(ctx.addressSort), sliceValue.type)
        val appended = unboxNamedRef(appendValue.accept(this).asExpr(ctx.addressSort), appendValue.type)
        val type = sliceValue.type.underlying()
        val isString = type == GoBasicTypes.STRING
        val elementSort = if (isString) ctx.bv8Sort else ctx.typeToSort((type as SliceType).elementType)
        val zero = ctx.mkSizeExpr(0)
        return scope.calcOnState {
            val view = arrayView(slice, type)
            val appendedView = arrayView(appended, appendValue.type)
            val length = ctx.mkSizeAddExpr(view.length, appendedView.length)
            checkLength(length) ?: throw GoStepAbort()
            val newBacking = memory.allocateGoArray(type, ctx.sizeSort, length)
            memory.copyGoArray(view.backing, newBacking, view.storageType, elementSort, view.offset, zero, view.length)
            memory.copyGoArray(
                appendedView.backing,
                newBacking,
                view.storageType,
                elementSort,
                appendedView.offset,
                view.length,
                appendedView.length
            )
            if (isString) return@calcOnState newBacking

            val reuse = ctx.mkSizeLeExpr(length, view.capacity)
            val backing = ctx.mkIte(reuse, view.backing, newBacking)
            val offset = ctx.mkIte(reuse, view.offset, zero)
            memory.copyGoArray(
                appendedView.backing,
                backing,
                view.storageType,
                elementSort,
                appendedView.offset,
                ctx.mkSizeAddExpr(offset, view.length),
                appendedView.length
            )
            val header = memory.allocateGoArray(type, ctx.sizeSort, length)
            data.arrayViews[header] = GoArrayView(
                backing, view.storageType, offset = offset,
                length = length, capacity = ctx.mkIte(reuse, view.capacity, length)
            )
            header
        }
    }

    private fun changeType(value: UExpr<out USort>, baseType: GoType, targetType: GoType): UExpr<out USort> {
        return when (targetType) {
            is NamedType -> {
                when (baseType) {
                    is NamedType -> {
                        box(unbox(value.asExpr(ctx.addressSort), ctx.typeToSort(baseType)), targetType)
                    }
                    else -> {
                        box(value, targetType)
                    }
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
        val funcName = when (func) {
            is GoParameter -> {
                func.name
            }
            is GoFunction -> {
                func.name
            }
            else -> {
                "unnamed"
            }
        }
        val signature = func.type as SignatureType
        val returnType = when (signature.results.types.size) {
            0 -> {
                NullType()
            }
            1 -> {
                signature.results.types[0]
            }
            else -> {
                signature.results
            }
        }
        val method = GoFunction(signature, emptyList(), funcName, emptyList(), "", emptyList(), emptyList())
        val mockSort = ctx.typeToSort(returnType)
        val mockValue = scope.calcOnState {
            memory.mocker.call(
                method,
                expr.args.map { it.accept(this@GoExprVisitor) }.asSequence(),
                mockSort,
                memory.ownership
            )
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
