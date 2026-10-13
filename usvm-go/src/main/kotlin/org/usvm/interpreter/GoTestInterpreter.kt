package org.usvm.interpreter

import io.ksmt.expr.KBitVec16Value
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KBitVec64Value
import io.ksmt.expr.KBitVec8Value
import io.ksmt.expr.KFp32Value
import io.ksmt.expr.KFp64Value
import io.ksmt.expr.KIteExpr
import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KBv16Sort
import io.ksmt.sort.KBv32Sort
import io.ksmt.sort.KBv64Sort
import io.ksmt.sort.KBv8Sort
import io.ksmt.sort.KFp32Sort
import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.go.api.ArrayType
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoType
import org.jacodb.go.api.InterfaceType
import org.jacodb.go.api.MapType
import org.jacodb.go.api.NamedType
import org.jacodb.go.api.NullType
import org.jacodb.go.api.PointerType
import org.jacodb.go.api.SignatureType
import org.jacodb.go.api.SliceType
import org.jacodb.go.api.StructType
import org.jacodb.go.api.TupleType
import org.usvm.GoContext
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UBvSort
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.USort
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapAnyKey
import org.usvm.api.makeSymbolicRefUntyped
import org.usvm.api.readField
import org.usvm.api.typeStreamOf
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.primitive.setEntries
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.set.ref.refSetEntries
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.isTrue
import org.usvm.memory.ULValue
import org.usvm.memory.UMemory
import org.usvm.memory.UWritableMemory
import org.usvm.memory.arrayView
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.memory.readGoArrayIndex
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeLeExpr
import org.usvm.model.UModelBase
import org.usvm.operator.GoBinaryOperator
import org.usvm.sampleUValue
import org.usvm.sizeSort
import org.usvm.solver.USatResult
import org.usvm.state.GoMethodResult
import org.usvm.state.GoState
import org.usvm.state.mapKeyCanonicalityConstraint
import org.usvm.state.mapKeyPayloadLValue
import org.usvm.state.mapKeySnapshotLValue
import org.usvm.type.GoBasicTypes
import org.usvm.type.GoVoidSort
import org.usvm.type.mapKeyStorageType
import org.usvm.type.needsMapKeyRepresentative
import org.usvm.type.underlying
import org.usvm.types.first
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.random.nextULong

class GoTestInterpreter(
    private val ctx: GoContext,
) {
    fun resolve(state: GoState, method: GoMethod): ProgramExecutionResult {
        var model = state.models.first()
        val boundedConstraints by lazy { state.clone().pathConstraints }
        val bounds = mutableSetOf<UBoolExpr>()

        repeat(MAX_MODEL_REFINEMENTS) {
            try {
                return resolveWithModel(state, method, model)
            } catch (limit: MaterializationBoundException) {
                if (!bounds.add(limit.bound)) {
                    throw UnsupportedOperationException(limit.failureMessage, limit)
                }
                model = refineModel(boundedConstraints, limit)
            }
        }
        throw UnsupportedOperationException("Go model size refinement limit exceeded")
    }

    private fun refineModel(
        constraints: UPathConstraints<GoType>,
        boundFailure: MaterializationBoundException,
    ): UModelBase<GoType> {
        constraints += boundFailure.bound
        val result = ctx.solver<GoType>().check(constraints)
        return (result as? USatResult)?.model ?: throw UnsupportedOperationException(
            "${boundFailure.failureMessage}: $result",
            boundFailure,
        )
    }

    private fun resolveWithModel(
        state: GoState,
        method: GoMethod,
        model: UModelBase<GoType>,
    ): ProgramExecutionResult = with(ctx) {
        // Keep input reads symbolic so size refinement can re-solve them without using the mutated heap.
        val inputMemory = UMemory<GoType, GoMethod>(ctx, MutabilityOwnership(), state.pathConstraints.typeConstraints)
        val inputScope = MemoryScope(ctx, state, model, inputMemory)
        val outputScope = MemoryScope(ctx, state, model, state.memory)

        val inputValues = List(method.parameters.size) { idx ->
            val type = method.parameters[idx].type as GoType
            val sort = typeToSort(type)
            val expr = mkRegisterReading(idx, sort)
            inputScope.convertExpr(expr, type)
        }
        val inputModel = InputModel(inputValues)
        val argumentsAfter = List(method.parameters.size) { index ->
            val type = method.parameters[index].type as GoType
            val valueSort = typeToSort(type.underlying())
            if (valueSort is UBvSort || valueSort is UFpSort || valueSort == boolSort) {
                // Scalar arguments are passed by value; local assignments cannot change the caller's value.
                inputValues[index]
            } else {
                val original = mkRegisterReading(index, typeToSort(type))
                outputScope.convertExpr(original, type)
            }
        }

        return if (state.isExceptional) {
            val panic = state.methodResult as GoMethodResult.Panic
            UnsuccessfulExecutionResult(inputModel, outputScope.convertExpr(panic.value, panic.type), argumentsAfter)
        } else {
            val result = state.methodResult as GoMethodResult.Success
            val expr = result.let { outputScope.convertExpr(it.value, it.type) }
            val outputModel = OutputModel(expr, argumentsAfter)

            SuccessfulExecutionResult(inputModel, outputModel)
        }
    }

    private class MemoryScope(
        private val ctx: GoContext,
        private val state: GoState,
        private val model: UModelBase<GoType>,
        private val memory: UWritableMemory<GoType>,
    ) {
        fun convertExpr(expr: UExpr<out USort>, baseType: GoType): Any? = when (expr.sort) {
            is GoVoidSort -> {
                ""
            }
            is KBoolSort -> {
                resolveBool(expr)
            }
            is KBv8Sort -> {
                val value = resolveBv8(expr)
                if (baseType == GoBasicTypes.UINT8) value.toUByte() else value
            }
            is KBv16Sort -> {
                val value = resolveBv16(expr)
                if (baseType == GoBasicTypes.UINT16) value.toUShort() else value
            }
            is KBv32Sort -> {
                val value = resolveBv32(expr)
                when (baseType) {
                    GoBasicTypes.UINT, GoBasicTypes.UINT32, GoBasicTypes.UINTPTR -> value.toUInt()
                    GoBasicTypes.RUNE -> Char(value)
                    else -> value
                }
            }
            is KBv64Sort -> {
                val value = resolveBv64(expr)
                val unsigned = baseType == GoBasicTypes.UINT ||
                    baseType == GoBasicTypes.UINT64 || baseType == GoBasicTypes.UINTPTR
                if (unsigned) value.toULong() else value
            }
            is KFp32Sort -> {
                resolveFp32(expr)
            }
            is KFp64Sort -> {
                resolveFp64(expr)
            }
            is UAddressSort -> {
                resolveReference(expr.asExpr(ctx.addressSort), baseType)
            }

            else -> {
                Any()
            }
        }

        private fun resolveReference(reference: UHeapRef, baseType: GoType): Any? {
            val type = baseType.underlying()
            if (baseType is NamedType && type !is InterfaceType) return resolveBoxed(reference, type)
            return when (type) {
                GoBasicTypes.STRING -> {
                    resolveString(reference, type)
                }
                is BasicType -> {
                    resolveBoxed(reference, type)
                }
                is ArrayType -> {
                    resolveArray(reference, type, type.len, type.elementType)
                }
                is SliceType -> {
                    resolveSlice(reference, type, type.elementType)
                }
                is MapType -> {
                    resolveMap(reference, type, type.keyType, type.valueType)
                }
                is TupleType -> {
                    resolveTuple(reference, type)
                }
                is StructType -> {
                    resolveStruct(reference, type)
                }
                is InterfaceType -> {
                    resolveInterface(reference)
                }
                is NullType -> {
                    null
                }
                is PointerType -> {
                    resolvePointer(reference, type.baseType)
                }
                is SignatureType -> {
                    GoFunctionReference(reference.toString())
                }
                else -> {
                    error("Cannot resolve Go type: $type")
                }
            }
        }

        fun resolveBool(expr: UExpr<out USort>) = model.eval(expr).asExpr(ctx.boolSort).isTrue

        fun resolveBv8(expr: UExpr<out USort>) = (model.eval(expr) as KBitVec8Value).byteValue

        fun resolveBv16(expr: UExpr<out USort>) = (model.eval(expr) as KBitVec16Value).shortValue

        fun resolveBv32(expr: UExpr<out USort>) = (model.eval(expr) as KBitVec32Value).intValue

        fun resolveBv64(expr: UExpr<out USort>) = (model.eval(expr) as KBitVec64Value).longValue

        fun resolveFp32(expr: UExpr<out USort>) = (model.eval(expr) as KFp32Value).value

        fun resolveFp64(expr: UExpr<out USort>) = (model.eval(expr) as KFp64Value).value

        fun resolveSize(expr: UExpr<out USort>) = (model.eval(expr) as KBitVec32Value).numberValue

        private fun resolveLength(expr: UExpr<USizeSort>): Int {
            val length = resolveSize(expr)
            if (length !in 0..MAX_ARRAY_LENGTH) {
                val bound = ctx.mkAnd(
                    ctx.mkSizeGeExpr(expr, ctx.mkSizeExpr(0)),
                    ctx.mkSizeLeExpr(expr, ctx.mkSizeExpr(MAX_ARRAY_LENGTH))
                )
                throw MaterializationBoundException(
                    bound,
                    failureMessage = "Cannot materialize Go model within size limit",
                )
            }
            return length
        }

        fun resolveString(string: UHeapRef, arrayType: GoType): String = with(ctx) {
            if (model.eval(string) == model.nullRef) {
                return ""
            }

            val view = state.arrayView(string, arrayType, sourceMemory = memory)
            val lengthUExpr = view.length
            val length = resolveLength(lengthUExpr)

            val buffer = ByteBuffer.allocate(length * Byte.SIZE_BYTES)
            for (i in 0..<length) {
                val element = memory.readGoArrayIndex(
                    view.backing,
                    ctx.mkSizeAddExpr(view.offset, mkSizeExpr(i)),
                    view.storageType,
                    bv8Sort
                )
                val byte = resolveBv8(element)
                buffer.put(byte)
            }

            val byteArray = ByteArray(buffer.position())
            buffer.flip().get(byteArray)
            return String(byteArray)
        }

        fun resolveArray(array: UHeapRef, arrayType: GoType, len: Long, elementType: GoType): List<Any?>? = with(ctx) {
            if (model.eval(array) == model.nullRef) {
                return null
            }

            val view = state.arrayView(array, arrayType, sourceMemory = memory)
            if (len !in 0..MAX_ARRAY_LENGTH.toLong()) {
                throw UnsupportedOperationException("Cannot materialize Go array of length $len")
            }
            val length = len.toInt()
            val sort = typeToSort(elementType)
            return List(length) { idx ->
                val element = memory.readGoArrayIndex(
                    view.backing,
                    mkBvAddExpr(view.offset, mkSizeExpr(idx)),
                    view.storageType,
                    sort
                )
                convertExpr(element, elementType)
            }
        }

        fun resolveSlice(slice: UHeapRef, sliceType: GoType, elementType: GoType): List<Any?>? = with(ctx) {
            if (model.eval(slice) == model.nullRef) {
                return null
            }

            val view = state.arrayView(slice, sliceType, sourceMemory = memory)
            val lengthUExpr = view.length
            val length = resolveLength(lengthUExpr)
            val sort = typeToSort(elementType)
            return List(length) { idx ->
                val offset = view.offset
                val index = ctx.mkBvAddExpr(offset, mkSizeExpr(idx))
                val element = memory.readGoArrayIndex(
                    view.backing,
                    index,
                    view.storageType,
                    sort
                )
                convertExpr(element, elementType)
            }
        }

        @Suppress("NestedBlockDepth") // Resolves both primitive and reference map regions.
        fun resolveMap(map: UHeapRef, mapType: GoType, keyType: GoType, valueType: GoType): Any? = with(
            ctx
        ) {
            if (model.eval(map) == model.nullRef) {
                return null
            }

            val keyStorageType = keyType.mapKeyStorageType()
            val keySort = mapKeySort(keyType)
            val valueSort = typeToSort(valueType)

            val isRefSet = keySort == addressSort

            val usesRepresentatives = keyType.needsMapKeyRepresentative()
            if (usesRepresentatives) {
                val canonicality = state.mapKeyCanonicalityConstraint(map, mapType as MapType, sourceMemory = memory)
                if (!resolveBool(canonicality)) throw MaterializationBoundException(canonicality)
            }
            val seenRepresentatives = mutableSetOf<UHeapRef>()
            val seenValueKeys = mutableSetOf<Any?>()
            val losslessEntries = mutableListOf<GoMapEntry>()
            val observedKeys = state.data.mapKeyRepresentatives[keyType].orEmpty()
                .filter { entry -> resolveBool(entry.guard) }
                .flatMap { entry -> listOf(entry.reference, entry.canonicalReference) }
                .toMutableSet()
            var needsLosslessWitness = false
            val addToMap: (
                MutableMap<Any?, Any?>,
                Set<ULValue<*, UBoolSort>>,
                UWritableMemory<GoType>,
                UHeapRef,
            ) -> Unit = { destination, entries, entryMemory, entryMap ->
                val entryScope = MemoryScope(ctx, state, model, entryMemory)
                destination.putAll(
                    entries.mapNotNull { entry ->
                        val key = when (entry) {
                            is URefSetEntryLValue<*> -> {
                                entry.setElement
                            }
                            is USetEntryLValue<*, *, *> -> {
                                entry.setElement
                            }
                            else -> {
                                error("Inconsistent Go interpreter state")
                            }
                        }

                        val membership = if (isRefSet) {
                            URefSetEntryLValue(entryMap, key.asExpr(addressSort), mapType)
                        } else {
                            USetEntryLValue(keySort, entryMap, key.asExpr(keySort), mapType, USizeExprKeyInfo())
                        }
                        if (usesRepresentatives) observedKeys += key.asExpr(addressSort)
                        if (!resolveBool(entryMemory.read(membership))) return@mapNotNull null
                        val decodedKey = entryScope.decodeMapKey(entryMap, key, mapType, keyType)
                        if (usesRepresentatives) {
                            val concrete = model.eval(key.asExpr(addressSort))
                            if (!seenRepresentatives.add(concrete)) return@mapNotNull null
                            needsLosslessWitness = needsGoMapEntryIdentity(decodedKey) ||
                                !seenValueKeys.add(decodedKey) || needsLosslessWitness
                        }

                        val lvalue = mapValueLValue(entryMap, key, mapType, keySort, valueSort)
                        val value = entryScope.convertExpr(entryMemory.read(lvalue), valueType)
                        if (usesRepresentatives) {
                            losslessEntries += GoMapEntry(decodedKey, value, identity = model.eval(key).toString())
                        }
                        decodedKey to value
                    }.toMap()
                )
            }
            val lengthExpression = memory.read(UMapLengthLValue(map, mapType, sizeSort))
            val length = resolveLength(lengthExpression)

            val result = mutableMapOf<Any?, Any?>()
            getMapEntries(memory, map, mapType, keySort).also { addToMap(result, it, memory, map) }
            val concreteMap = model.eval(map)
            getMapEntries(model, concreteMap, mapType, keySort).also { addToMap(result, it, model, concreteMap) }

            if (usesRepresentatives) {
                ensureMapEntriesMaterialized(
                    map,
                    mapType,
                    keyType,
                    observedKeys,
                    lengthExpression,
                    length = length,
                    observedCount = losslessEntries.size,
                )
                return if (needsLosslessWitness) GoMapEntries(losslessEntries.toList()) else result
            }

            if (length > result.size) {
                val diff = length - result.size
                val rng = if (isRefSet) null else RNG(keyStorageType as BasicType)
                for (i in 0 until diff) {
                    val key = if (isRefSet) {
                        convertExpr(state.symbolicObjectMapAnyKey(map, mapType), keyType)
                    } else {
                        requireNotNull(rng).generateUniqueMapKey(result)
                    }
                    val value = convertExpr(valueSort.sampleUValue(), valueType)
                    result[key] = value
                }
            }

            return result
        }

        private fun mapValueLValue(
            map: UHeapRef,
            key: UExpr<out USort>,
            mapType: GoType,
            keySort: USort,
            valueSort: USort,
        ): ULValue<*, USort> {
            val isReferenceKey = keySort == ctx.addressSort
            return if (isReferenceKey) {
                URefMapEntryLValue(valueSort, map, key.asExpr(ctx.addressSort), mapType)
            } else {
                UMapEntryLValue(keySort, valueSort, map, key.asExpr(keySort), mapType, USizeExprKeyInfo())
            }
        }

        private fun getMapEntries(
            source: UWritableMemory<GoType>,
            map: UHeapRef,
            mapType: GoType,
            keySort: USort,
        ): Set<ULValue<*, UBoolSort>> {
            val isReferenceKey = keySort == ctx.addressSort
            return if (isReferenceKey) {
                source.refSetEntries(map, mapType).entries
            } else {
                source.setEntries(map, mapType, keySort, USizeExprKeyInfo()).entries
            }
        }

        private fun decodeMapKey(map: UHeapRef, key: UExpr<out USort>, mapType: GoType, keyType: GoType): Any? {
            val usesRepresentatives = keyType.needsMapKeyRepresentative()
            if (usesRepresentatives) {
                val stored = memory.read(ctx.mapKeyPayloadLValue(map, key.asExpr(ctx.addressSort), mapType as MapType))
                return convertStoredMapKey(stored, keyType)
            }

            return convertExpr(key, keyType.mapKeyStorageType())
        }

        private fun ensureMapEntriesMaterialized(
            map: UHeapRef,
            mapType: GoType,
            keyType: GoType,
            observedKeys: Set<UHeapRef>,
            lengthExpression: UExpr<USizeSort>,
            length: Int,
            observedCount: Int,
        ): Unit = with(ctx) {
            if (length == observedCount) return

            // Unobserved entries need a solver-backed witness, never invented keys or values.
            val canGenerateKeys = keyType.underlying() in setOf(
                GoBasicTypes.FLOAT32,
                GoBasicTypes.FLOAT64,
                GoBasicTypes.STRING,
            )
            val canGenerateHiddenEntries = length > observedCount && canGenerateKeys
            val bound = if (canGenerateHiddenEntries) {
                constrainHiddenMapEntries(
                    map,
                    mapType,
                    keyType,
                    observedKeys,
                    lengthExpression,
                    length = length,
                    hiddenCount = minOf(length - observedCount, MAX_NEW_MAP_KEYS_PER_REFINEMENT),
                )
            } else {
                mkEq(lengthExpression, mkSizeExpr(observedCount))
            }
            throw MaterializationBoundException(bound)
        }

        private fun convertStoredMapKey(value: UExpr<out USort>, type: GoType): Any? {
            val underlying = type.underlying()
            val referenceZero = value.sort == ctx.addressSort &&
                model.eval(value.asExpr(ctx.addressSort)) == model.nullRef
            if (referenceZero) return zeroStoredMapKey(underlying)

            return convertExpr(value, type)
        }

        private fun zeroStoredMapKey(type: GoType): Any? = when (val underlying = type.underlying()) {
            is ArrayType -> {
                val bounded = underlying.len in 0..MAX_ARRAY_LENGTH.toLong()
                if (!bounded) {
                    throw UnsupportedOperationException(
                        "Cannot materialize zero Go array key of length ${underlying.len}",
                    )
                }

                List(underlying.len.toInt()) { zeroStoredMapKey(underlying.elementType) }
            }
            is StructType -> {
                underlying.fields.orEmpty().mapIndexed { index, field ->
                    "field$index" to zeroStoredMapKey(field)
                }.toMap()
            }
            else -> {
                convertExpr(ctx.typeToSort(underlying).sampleUValue(), underlying)
            }
        }

        private fun mapKeyPayload(
            key: UHeapRef,
            type: GoType,
            map: UHeapRef,
            mapType: MapType,
        ): Pair<UExpr<out USort>, UWritableMemory<GoType>> = with(ctx) {
            val known = state.data.mapKeyRepresentatives[type].orEmpty().firstOrNull {
                model.eval(it.reference) == model.eval(key) && resolveBool(it.guard)
            }
            val source = if (known == null) model else state.memory
            val snapshot = known?.snapshot ?: source.read(
                ctx.mapKeyPayloadLValue(model.eval(map), model.eval(key), mapType),
            )
            val underlying = type.underlying()
            val sort = typeToSort(underlying)
            val payload = if (type is NamedType) {
                mkIte(
                    mkHeapRefEq(snapshot.asExpr(addressSort), nullRef),
                    sort.sampleUValue(),
                    source.readField(snapshot.asExpr(addressSort), field = 0, sort = sort),
                )
            } else {
                snapshot
            }
            payload to source
        }

        private fun constrainHiddenMapEntries(
            map: UHeapRef,
            mapType: GoType,
            keyType: GoType,
            observed: Set<UHeapRef>,
            lengthExpression: UExpr<USizeSort>,
            length: Int,
            hiddenCount: Int,
        ): UBoolExpr = with(ctx) {
            val underlying = keyType.underlying()
            val keys = observed.toMutableList()
            val payloads = keys.map { mapKeyPayload(it, keyType, map, mapType as MapType).first }.toMutableList()
            val stringLengthExpressions = if (underlying == GoBasicTypes.STRING) {
                keys.map { key ->
                    val (payload, source) = mapKeyPayload(key, keyType, map, mapType as MapType)
                    state.arrayView(payload.asExpr(addressSort), underlying, sourceMemory = source).length
                }.toMutableList()
            } else {
                mutableListOf()
            }
            val usedStringLengths = stringLengthExpressions.map(::resolveLength).toMutableSet()
            var constraints = mkEq(lengthExpression, mkSizeExpr(length))
            repeat(hiddenCount) {
                val fresh = state.makeSymbolicRefUntyped()
                val snapshot = memory.read(ctx.mapKeySnapshotLValue(fresh, keyType))
                val sort = typeToSort(underlying)
                val payload = if (keyType is NamedType) {
                    mkIte(
                        mkHeapRefEq(snapshot.asExpr(addressSort), nullRef),
                        sort.sampleUValue(),
                        memory.readField(snapshot.asExpr(addressSort), field = 0, sort = sort),
                    )
                } else {
                    snapshot
                }
                constraints = mkAnd(
                    constraints,
                    memory.read(URefSetEntryLValue(map, fresh, mapType)),
                    mkEq(memory.read(ctx.mapKeyPayloadLValue(map, fresh, mapType as MapType)), snapshot),
                    mkNot(mkHeapRefEq(fresh, nullRef)),
                )
                for (key in keys) constraints = mkAnd(constraints, mkNot(mkHeapRefEq(fresh, key)))
                if (underlying == GoBasicTypes.STRING) {
                    val nextLength = (0..MAX_ARRAY_LENGTH).firstOrNull { candidateLength ->
                        candidateLength !in usedStringLengths
                    } ?: throw UnsupportedOperationException("No bounded distinct string key for Go map witness")
                    usedStringLengths += nextLength
                    val view = state.arrayView(payload.asExpr(addressSort), underlying, sourceMemory = memory)
                    constraints = mkAnd(constraints, mkEq(view.length, mkSizeExpr(nextLength)))
                    for (other in stringLengthExpressions) {
                        constraints = mkAnd(constraints, mkNot(mkEq(view.length, other)))
                    }
                    stringLengthExpressions += view.length
                    if (nextLength > 0) {
                        constraints = mkAnd(constraints, mkNot(mkHeapRefEq(payload.asExpr(addressSort), nullRef)))
                    }
                    for (index in 0 until nextLength) {
                        val byte = memory.readGoArrayIndex(
                            view.backing,
                            mkSizeAddExpr(view.offset, mkSizeExpr(index)),
                            view.storageType,
                            bv8Sort,
                        )
                        val asciiByte = mkBvUnsignedLessOrEqualExpr(byte, mkBv(MAX_ASCII_BYTE, bv8Sort))
                        constraints = mkAnd(constraints, asciiByte)
                    }
                } else {
                    val reflexive = GoBinaryOperator.Eql(payload, payload).asExpr(boolSort)
                    constraints = mkAnd(constraints, reflexive)
                    for (other in payloads) {
                        val equal = GoBinaryOperator.Eql(payload, other).asExpr(boolSort)
                        constraints = mkAnd(constraints, mkNot(equal))
                    }
                }
                keys += fresh
                payloads += payload
            }
            constraints
        }

        fun resolveTuple(tuple: UHeapRef, tupleType: TupleType): List<Any?>? = with(ctx) {
            if (model.eval(tuple) == model.nullRef) {
                return null
            }

            return List(tupleType.types.size) {
                val sort = typeToSort(tupleType.types[it])
                convertExpr(memory.readField(tuple, it, sort), tupleType.types[it])
            }
        }

        fun resolveStruct(struct: UHeapRef, structType: StructType): Map<String, Any?>? = with(ctx) {
            if (model.eval(struct) == model.nullRef) {
                return null
            }

            return structType.fields?.mapIndexed { idx, type -> idx to type }?.associate {
                Pair(
                    "field${it.first}",
                    convertExpr(memory.readField(struct, it.first, typeToSort(it.second)), it.second)
                )
            }
        }

        fun resolveInterface(iface: UHeapRef): Any? = with(ctx) {
            if (model.eval(iface) == model.nullRef) {
                return null
            }

            val type = if (org.usvm.isAllocatedConcreteHeapRef(iface)) {
                state.memory.typeStreamOf(iface).first()
            } else {
                model.typeStreamOf(model.eval(iface)).first()
            }
            val index = 0
            val value = if (type is NamedType) {
                convertExpr(iface, type)
            } else {
                convertExpr(memory.readField(iface, index, typeToSort(type)), type)
            }
            return GoInterfaceValue(type, value)
        }

        fun resolvePointer(pointer: UHeapRef, baseType: GoType): Any? = with(ctx) {
            if (pointer is KIteExpr) {
                val branch = if (resolveBool(pointer.condition)) pointer.trueBranch else pointer.falseBranch
                return resolvePointer(branch, baseType)
            }
            state.data.pointerConversions[pointer]?.let { conversion ->
                return resolvePointer(conversion.source, conversion.sourceType)
            }

            if (model.eval(pointer) == model.nullRef) {
                return null
            }

            val index = 0
            val target = state.data.pointerTargets[pointer]
            val expr = if (target == null) {
                memory.readField(
                    pointer,
                    index,
                    ctx.typeToSort(baseType)
                )
            } else {
                memory.read(target)
            }
            return GoPointer(convertExpr(expr, baseType))
        }

        fun resolveBoxed(value: UHeapRef, type: GoType): Any? = with(ctx) {
            if (model.eval(value) == model.nullRef) {
                return null
            }

            val index = 0
            return convertExpr(memory.readField(value, index, typeToSort(type)), type)
        }
    }

    private companion object {
        const val MAX_ARRAY_LENGTH = 10_000
        const val MAX_MODEL_REFINEMENTS = 32
        const val MAX_NEW_MAP_KEYS_PER_REFINEMENT = 64
        const val MAX_ASCII_BYTE = 127
    }

    private class MaterializationBoundException(
        val bound: UBoolExpr,
        val failureMessage: String = "Cannot materialize Go model under witness constraints",
    ) : RuntimeException()
}

sealed interface ProgramExecutionResult

class InputModel(
    val arguments: List<Any?>,
) {
    override fun toString(): String {
        return buildString {
            appendLine("InputModel")
            val arguments = arguments.joinToString(", ", "Arguments [", "]")
            appendLine(arguments.prependIndent("\t"))
        }
    }
}

class OutputModel(
    val returnExpr: Any?,
    val argumentsAfter: List<Any?>,
) {
    override fun toString(): String {
        return buildString {
            appendLine("OutputModel")
            val returnString = "Return [$returnExpr]"
            appendLine(returnString.prependIndent("\t"))
        }
    }
}

class SuccessfulExecutionResult(
    val inputModel: InputModel,
    val outputModel: OutputModel,
) : ProgramExecutionResult {
    override fun toString(): String {
        return buildString {
            appendLine("================================================================")
            appendLine("Successful Execution")
            appendLine("----------------------------------------------------------------")
            appendLine(inputModel.toString())
            appendLine("----------------------------------------------------------------")
            appendLine(outputModel.toString())
            appendLine("================================================================")
        }
    }
}

class UnsuccessfulExecutionResult(
    val inputModel: InputModel,
    val panicValue: Any?,
    val argumentsAfter: List<Any?>,
) : ProgramExecutionResult {
    override fun toString(): String {
        return buildString {
            appendLine("================================================================")
            appendLine("Unsuccessful Execution")
            appendLine("----------------------------------------------------------------")
            appendLine(inputModel.toString())
            appendLine("----------------------------------------------------------------")
            appendLine(panicValue)
            appendLine("================================================================")
        }
    }
}

class RNG(
    type: BasicType,
    seed: Long = 0,
    private val maxAttempts: Long = 10,
) {
    private val random = Random(seed)

    private val generate: () -> Any = when (type) {
        GoBasicTypes.INT8 -> {
            { random.nextBytes(1).first() }
        }

        GoBasicTypes.UINT8 -> {
            { random.nextBytes(1).first().toUByte() }
        }

        GoBasicTypes.INT16 -> {
            { random.nextInt().toShort() }
        }

        GoBasicTypes.UINT16 -> {
            { random.nextInt().toUShort() }
        }

        GoBasicTypes.INT, GoBasicTypes.INT32 -> {
            { random.nextInt() }
        }

        GoBasicTypes.UINT, GoBasicTypes.UINT32 -> {
            { random.nextUInt() }
        }

        GoBasicTypes.INT64 -> {
            { random.nextLong() }
        }

        GoBasicTypes.UINT64, GoBasicTypes.UINTPTR -> {
            { random.nextULong() }
        }

        GoBasicTypes.FLOAT32 -> {
            { random.nextFloat() }
        }

        GoBasicTypes.FLOAT64 -> {
            { random.nextDouble() }
        }

        else -> {
            error("Inconsistent Go interpreter state")
        }
    }

    fun generateUniqueMapKey(map: Map<Any?, Any?>): Any? {
        for (i in 0 until maxAttempts) {
            val key = generate()
            if (!map.containsKey(key)) {
                return key
            }
        }

        return null
    }
}

/** A function supplied to a mocked call; it cannot currently be replayed as a concrete Go function. */
data class GoFunctionReference(val expression: String)

data class GoPointer(val value: Any?) {
    override fun toString(): String = "&$value"
}

data class GoInterfaceValue(val type: GoType, val value: Any?) {
    override fun toString(): String = value.toString()
}
