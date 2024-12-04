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
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.api.readField
import org.usvm.api.refSetContainsElement
import org.usvm.api.setContainsElement
import org.usvm.api.writeArrayLength
import org.usvm.api.writeField
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.interpreter.GoStepScope
import org.usvm.memory.GoPointerLValue
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.operator.GoBinaryOperator
import org.usvm.operator.GoUnaryOperator
import org.usvm.operator.mkNarrow
import org.usvm.state.GoMethodResult
import org.usvm.statistics.ApplicationGraph
import org.usvm.type.GoBasicTypes
import org.usvm.type.underlying

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
            return result.value
        }

        val method = when {
            expr.callee != null -> expr.callee!!
            func is GoFunction -> pkg.findMethod(func.metName)
            func is GoVar -> scope.calcOnState {
                pkg.findMethod((memory.read(URegisterStackLValue(ctx.addressSort, index(func.name))) as KConst<*>).decl.name)
            }
            else -> throw UnknownFunctionException()
        }
        val parameters = expr.args.map { it.accept(this) }.toTypedArray()
        val call = GoCall(method, applicationGraph.entryPoints(method).first(), parameters)
        ctx.setMethodInfo(method, parameters)

        scope.doWithState {
            addCall(call, currentStatement)
        }
        return ctx.voidValue
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
        TODO("Not yet implemented")
    }

    override fun visitGoConvertExpr(expr: GoConvertExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoMultiConvertExpr(expr: GoMultiConvertExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoChangeInterfaceExpr(expr: GoChangeInterfaceExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoSliceToArrayPointerExpr(expr: GoSliceToArrayPointerExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoMakeInterfaceExpr(expr: GoMakeInterfaceExpr): UExpr<out USort> {
        val index = (expr.toAssignInst().lhv.accept(this) as KBitVec32Value).intValue
        val rvalue = expr.value.accept(this).let {
            if (it.sort == ctx.addressSort) {
                it
            } else {
                scope.calcOnState {
                    val ref = memory.allocConcrete(expr.type)
                    memory.writeField(ref, index, it.sort, it.asExpr(it.sort), ctx.trueExpr)
                    ref
                }
            }
        }.asExpr(ctx.addressSort)

        scope.doWithState {
            scope.assert(memory.types.evalIsSubtype(rvalue, expr.value.type)) ?: throw IllegalStateException()
        }

        return rvalue
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
            val ref = memory.allocConcrete(expr.type)
            memory.writeArrayLength(ref, len, expr.type, ctx.sizeSort)
            ref.asExpr(ctx.addressSort)
        }
    }

    override fun visitGoSliceExpr(expr: GoSliceExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoFieldAddrExpr(expr: GoFieldAddrExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoFieldExpr(expr: GoFieldExpr): UExpr<out USort> {
        TODO("Not yet implemented")
    }

    override fun visitGoIndexAddrExpr(expr: GoIndexAddrExpr): UExpr<out USort> {
        val array = expr.instance.accept(this).asExpr(ctx.addressSort)
        val index = expr.index.accept(this).asExpr(ctx.sizeSort)
        val length = scope.calcOnState { memory.readArrayLength(array, expr.instance.type, ctx.sizeSort) }

        scope.assert(ctx.mkSizeGeExpr(length, ctx.mkSizeExpr(0)))
        checkNotNull(array) ?: throw IllegalStateException()
        checkNegativeIndex(index) ?: throw IllegalStateException()
        checkIndexOutOfBounds(index, length) ?: throw IllegalStateException()

        return scope.calcOnState {
            val type = (expr.type as PointerType).baseType
            val sort = ctx.typeToSort(type)
            val ref = memory.allocConcrete(type)
            val element = UArrayIndexLValue(sort, array, index, expr.instance.type)
            memory.write(GoPointerLValue(ref, sort), ctx.mkLValuePointer(element), ctx.trueExpr)
            ctx.mkAddressPointer(ref.address)
        }
    }

    override fun visitGoIndexExpr(expr: GoIndexExpr): UExpr<out USort> {
        TODO("Not yet implemented")
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
            if (expr.instance.type is MapType) copyMap(it, expr.instance.type as MapType) else it
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
            val tupleType = memory.types.getTypeStream(iter).commonSuperType as TupleType
            val collection = memory.readField(iter, 0, ctx.addressSort)
            val notNull = ctx.mkNot(ctx.mkHeapRefEq(collection, ctx.nullRef))
            when (val collectionType = tupleType.types[0]) {
                is BasicType -> {
                    val index = memory.readField(iter, 1, ctx.sizeSort)
                    val char = memory.readArrayIndex(collection, index, collectionType, ctx.bv32Sort)
                    val length = memory.readArrayLength(collection, collectionType, ctx.sizeSort)
                    val ok = ctx.mkAnd(notNull, ctx.mkBvSignedLessExpr(index, length))

                    memory.writeField(iter, 1, ctx.sizeSort, ctx.mkBvAddExpr(index, ctx.mkSizeExpr(1)), ctx.trueExpr)
                    mkTuple(
                        TupleType(listOf(GoBasicTypes.BOOL, GoBasicTypes.INT32, GoBasicTypes.INT32)),
                        ok, index, char
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
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    fun <Sort : USort> pointerLValue(pointer: UAddressPointer, sort: Sort): ULValue<*, Sort> = with(ctx) {
        return scope.calcOnState {
            val lvalue = GoPointerLValue(mkConcreteHeapRef(pointer.address), sort)
            val ref = memory.read(lvalue)
            if (ref is ULValuePointer) {
                ref.lvalue.withSort(sort)
            } else {
                lvalue
            }
        }
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
            else -> throw UnknownBinaryOperationException()
        }(expr.lhv.accept(this), normalize(expr.rhv.accept(this), expr, signed))
    }

    private fun visitGoUnaryExpr(expr: GoUnaryExpr): UExpr<out USort> {
        val x = expr.value.accept(this)
        return when (expr) {
            is GoUnArrowExpr -> throw UnknownUnaryOperationException()
            is GoUnXorExpr, is GoUnNotExpr, is GoUnSubExpr -> GoUnaryOperator.Neg(x)
            is GoUnMulExpr -> deref(x, ctx.typeToSort(expr.type))
            else -> throw UnknownUnaryOperationException()
        }
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

    fun checkNotNull(obj: UHeapRef): Unit? = with(ctx) {
        scope.fork(mkHeapRefEq(obj, nullRef).not(), blockOnFalseState = {
            panic("null")
        })
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

    private fun callBuiltin(method: GoBuiltin, args: List<GoValue>): UExpr<out USort> {
        return when (method.name) {
            "len" -> {
                val arg = args[0]
                val collection = arg.accept(this).asExpr(ctx.addressSort)
                checkNotNull(collection) ?: throw IllegalStateException()

                return scope.calcOnState {
                    when(val type = arg.type) {
                        is ArrayType, is SliceType -> memory.readArrayLength(collection, arg.type, ctx.sizeSort)
                        is MapType -> symbolicObjectMapSize(collection, type)
                        else -> throw IllegalStateException()
                    }

                }
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
}