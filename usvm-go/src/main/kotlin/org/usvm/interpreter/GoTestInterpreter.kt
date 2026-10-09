package org.usvm.interpreter

import io.ksmt.expr.KBitVec16Value
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KBitVec64Value
import io.ksmt.expr.KBitVec8Value
import io.ksmt.expr.KFp32Value
import io.ksmt.expr.KFp64Value
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
import org.usvm.NULL_ADDRESS
import org.usvm.UAddressSort
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapAnyKey
import org.usvm.api.readField
import org.usvm.api.typeStreamOf
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.primitive.setEntries
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.set.ref.refSetEntries
import org.usvm.interpreter.GoInterpreter.Companion.logger
import org.usvm.isTrue
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.UWritableMemory
import org.usvm.memory.arrayView
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.memory.readGoArrayIndex
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.sampleUValue
import org.usvm.sizeSort
import org.usvm.state.GoMethodResult
import org.usvm.state.GoState
import org.usvm.type.GoBasicTypes
import org.usvm.type.GoVoidSort
import org.usvm.type.underlying
import org.usvm.types.first
import java.nio.ByteBuffer
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.random.nextULong

class GoTestInterpreter(
    private val ctx: GoContext,
) {
    fun resolve(state: GoState, method: GoMethod): ProgramExecutionResult = with(ctx) {
        val model = state.models.first()

        val inputScope = MemoryScope(ctx, state, model, model)
        val outputScope = MemoryScope(ctx, state, model, state.memory)

        val inputValues = List(method.parameters.size) { idx ->
            val type = method.parameters[idx].type as GoType
            val sort = typeToSort(type)
            val expr = model.read(URegisterStackLValue(sort, idx))
            inputScope.convertExpr(expr, type)
        }
        val inputModel = InputModel(inputValues)
        val argumentsAfter = List(method.parameters.size) { index ->
            val type = method.parameters[index].type as GoType
            val original = model.read(URegisterStackLValue(typeToSort(type), index))
            outputScope.convertExpr(original, type)
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
            if (baseType is NamedType) return resolveBoxed(reference, type)
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

        fun resolveString(string: UHeapRef, arrayType: GoType): String = with(ctx) {
            if (string == mkConcreteHeapRef(NULL_ADDRESS) || string == nullRef) {
                return ""
            }

            val view = state.arrayView(string, arrayType)
            val lengthUExpr = view.length
            val length = clipArrayLength(resolveSize(lengthUExpr))

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
            if (array == mkConcreteHeapRef(NULL_ADDRESS) || array == nullRef) {
                return null
            }

            val view = state.arrayView(array, arrayType)
            val length = clipArrayLength(len.toInt())
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
            if (slice == mkConcreteHeapRef(NULL_ADDRESS) || slice == nullRef) {
                return null
            }

            val view = state.arrayView(slice, sliceType)
            val lengthUExpr = view.length
            val length = clipArrayLength(resolveSize(lengthUExpr))
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
        fun resolveMap(map: UHeapRef, mapType: GoType, keyType: GoType, valueType: GoType): Map<Any?, Any?>? = with(
            ctx
        ) {
            if (map == mkConcreteHeapRef(NULL_ADDRESS) || map == nullRef) {
                return null
            }

            val keySort = typeToSort(keyType)
            val valueSort = typeToSort(valueType)

            val isRefSet = keySort == addressSort

            val addToMap: (MutableMap<Any?, Any?>, Set<ULValue<*, UBoolSort>>) -> Unit = { m, s ->
                m.putAll(
                    s.associate { entry ->
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

                        val lvalue = if (isRefSet) {
                            URefMapEntryLValue(valueSort, map, key.asExpr(addressSort), mapType)
                        } else {
                            UMapEntryLValue(keySort, valueSort, map, key.asExpr(keySort), mapType, USizeExprKeyInfo())
                        }
                        val value = memory.read(lvalue)
                        convertExpr(key, keyType) to convertExpr(value, valueType)
                    }
                )
            }
            val getEntries: (UHeapRef) -> Set<ULValue<*, UBoolSort>> = {
                if (isRefSet) {
                    memory.refSetEntries(it, mapType)
                } else {
                    memory.setEntries(it, mapType, keySort, USizeExprKeyInfo())
                }.entries
            }

            val length = clipArrayLength(resolveSize(memory.read(UMapLengthLValue(map, mapType, sizeSort))))

            val result = mutableMapOf<Any?, Any?>()
            getEntries(map).also { addToMap(result, it) }
            getEntries(model.eval(map)).also { addToMap(result, it) }

            if (length > result.size) {
                val diff = length - result.size
                val rng = RNG(keyType as BasicType)
                for (i in 0 until diff) {
                    val key = if (isRefSet) {
                        convertExpr(state.symbolicObjectMapAnyKey(map, mapType), keyType)
                    } else {
                        rng.generateUniqueMapKey(result)
                    }
                    val value = convertExpr(valueSort.sampleUValue(), valueType)
                    result[key] = value
                }
            }

            return result
        }

        fun resolveTuple(tuple: UHeapRef, tupleType: TupleType): List<Any?>? = with(ctx) {
            if (tuple == mkConcreteHeapRef(NULL_ADDRESS) || tuple == nullRef) {
                return null
            }

            return List(tupleType.types.size) {
                val sort = typeToSort(tupleType.types[it])
                convertExpr(memory.readField(tuple, it, sort), tupleType.types[it])
            }
        }

        fun resolveStruct(struct: UHeapRef, structType: StructType): Map<String, Any?>? = with(ctx) {
            if (struct == mkConcreteHeapRef(NULL_ADDRESS) || struct == nullRef) {
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
            if (iface == mkConcreteHeapRef(NULL_ADDRESS) || iface == nullRef) {
                return null
            }

            val type = memory.typeStreamOf(iface).first()
            val index = 0
            return GoInterfaceValue(type, convertExpr(memory.readField(iface, index, typeToSort(type)), type))
        }

        fun resolvePointer(pointer: UHeapRef, baseType: GoType): Any? = with(ctx) {
            if (pointer == mkConcreteHeapRef(NULL_ADDRESS) || pointer == nullRef) {
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
            if (value == mkConcreteHeapRef(NULL_ADDRESS) || value == nullRef) {
                return null
            }

            val index = 0
            return convertExpr(memory.readField(value, index, typeToSort(type)), type)
        }
    }

    companion object {
        fun clipArrayLength(length: Int): Int =
            when {
                length in 0..MAX_ARRAY_LENGTH -> {
                    length
                }

                length > MAX_ARRAY_LENGTH -> {
                    logger.warn { "Array length exceeds $MAX_ARRAY_LENGTH: $length" }
                    MAX_ARRAY_LENGTH
                }

                else -> {
                    logger.warn { "Negative array length: $length" }
                    0
                }
            }

        private const val MAX_ARRAY_LENGTH = 10_000
    }
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
