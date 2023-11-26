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
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
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
import org.usvm.memory.key.USizeExprKeyInfo
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

        return if (state.isExceptional) {
            val panic = state.methodResult as GoMethodResult.Panic
            UnsuccessfulExecutionResult(inputModel, outputScope.convertExpr(panic.value, panic.type))
        } else {
            val result = state.methodResult as GoMethodResult.Success
            val expr = result.let { outputScope.convertExpr(it.value, it.type) }
            val outputModel = OutputModel(expr)

            SuccessfulExecutionResult(inputModel, outputModel)
        }
    }

    private class MemoryScope(
        private val ctx: GoContext,
        private val state: GoState,
        private val model: UModelBase<GoType>,
        private val memory: UWritableMemory<GoType>,
    ) {
        fun convertExpr(expr: UExpr<out USort>, baseType: GoType): Any = when (expr.sort) {
            is GoVoidSort -> ""
            is KBoolSort -> resolveBool(expr)
            is KBv8Sort -> resolveBv8(expr)
            is KBv16Sort -> resolveBv16(expr)
            is KBv32Sort -> resolveBv32(expr).let { if (baseType == GoBasicTypes.RUNE) Char(it) else it }
            is KBv64Sort -> resolveBv64(expr)
            is KFp32Sort -> resolveFp32(expr)
            is KFp64Sort -> resolveFp64(expr)
            is UAddressSort -> when (expr.sort) {
                ctx.addressSort -> {
                    val h = expr.asExpr(ctx.addressSort)
                    val named = baseType is NamedType
                    val prefix = if (named) (baseType as NamedType).name + ": " else ""
                    prefix + when (val type = baseType.underlying()) {
                        GoBasicTypes.STRING -> if (named) resolveBoxed(h, type) else resolveString(h, type)
                        is BasicType -> resolveBoxed(h, type)
                        is ArrayType -> if (named) resolveBoxed(h, type) else resolveArray(h, type, type.len, type.elementType)
                        is SliceType -> if (named) resolveBoxed(h, type) else resolveSlice(h, type, type.elementType)
                        is MapType -> if (named) resolveBoxed(h, type) else resolveMap(h, type, type.keyType, type.valueType)
                        is TupleType -> resolveTuple(h, type)
                        is StructType -> if (named) resolveBoxed(h, type) else resolveStruct(h, type)
                        is InterfaceType -> resolveInterface(h)
                        is NullType -> null
                        is PointerType -> resolvePointer(h, type.baseType)
                        else -> Any()
                    }
                }

                else -> Any()
            }

            else -> Any()
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

            val lengthUExpr = memory.readArrayLength(string, arrayType, sizeSort)
            val length = clipArrayLength(resolveSize(lengthUExpr))

            val buffer = ByteBuffer.allocate(length * Byte.SIZE_BYTES)
            for (i in 0..<length) {
                val element = memory.readArrayIndex(string, mkSizeExpr(i), arrayType, bv8Sort)
                val byte = convertExpr(element, GoBasicTypes.UINT8) as Byte
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

            val length = clipArrayLength(len.toInt())
            val sort = typeToSort(elementType)
            return List(length) { idx ->
                val element = memory.readArrayIndex(array, mkSizeExpr(idx), arrayType, sort)
                convertExpr(element, elementType)
            }
        }

        fun resolveSlice(slice: UHeapRef, sliceType: GoType, elementType: GoType): List<Any?>? = with(ctx) {
            if (slice == mkConcreteHeapRef(NULL_ADDRESS) || slice == nullRef) {
                return null
            }

            val lengthUExpr = memory.readArrayLength(slice, sliceType, sizeSort)
            val length = clipArrayLength(resolveSize(lengthUExpr))
            val sort = typeToSort(elementType)
            return List(length) { idx ->
                val element = memory.readArrayIndex(slice, mkSizeExpr(idx), sliceType, sort)
                convertExpr(element, elementType)
            }
        }

        fun resolveMap(map: UHeapRef, mapType: GoType, keyType: GoType, valueType: GoType): Map<Any?, Any?>? = with(ctx) {
            if (map == mkConcreteHeapRef(NULL_ADDRESS) || map == nullRef) {
                return null
            }

            val keySort = typeToSort(keyType)
            val valueSort = typeToSort(valueType)

            val isRefSet = keySort == addressSort

            val addToMap: (MutableMap<Any?, Any?>, Set<ULValue<*, UBoolSort>>) -> Unit = { m, s ->
                m.putAll(s.associate { entry ->
                    val key = when (entry) {
                        is URefSetEntryLValue<*> -> entry.setElement
                        is USetEntryLValue<*, *, *> -> entry.setElement
                        else -> throw IllegalStateException()
                    }

                    val lvalue = if (isRefSet) {
                        URefMapEntryLValue(valueSort, map, key.asExpr(addressSort), mapType)
                    } else {
                        UMapEntryLValue(keySort, valueSort, map, key.asExpr(keySort), mapType, USizeExprKeyInfo())
                    }
                    val value = memory.read(lvalue)
                    convertExpr(key, keyType) to convertExpr(value, valueType)
                })
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
                Pair("field${it.first}", convertExpr(memory.readField(struct, it.first, typeToSort(it.second)), it.second))
            }
        }

        fun resolveInterface(iface: UHeapRef): Any? = with(ctx) {
            if (iface == mkConcreteHeapRef(NULL_ADDRESS) || iface == nullRef) {
                return null
            }

            val type = memory.typeStreamOf(iface).first()
            val index = 0
            return convertExpr(memory.readField(iface, index, typeToSort(type)), type)
        }

        fun resolvePointer(pointer: UHeapRef, baseType: GoType): Any? = with(ctx) {
            if (pointer == mkConcreteHeapRef(NULL_ADDRESS) || pointer == nullRef) {
                return null
            }

            val index = 0
            val expr = memory.readField(pointer, index, ctx.typeToSort(baseType))
            return "&" + convertExpr(expr, baseType)
        }

        fun resolveBoxed(value: UHeapRef, type: GoType): Any? = with(ctx) {
            if (value == mkConcreteHeapRef(NULL_ADDRESS)) {
                return null
            }

            val index = 0
            return convertExpr(memory.readField(value, index, typeToSort(type)), type)
        }
    }

    companion object {
        fun clipArrayLength(length: Int): Int =
            when {
                length in 0..MAX_ARRAY_LENGTH -> length

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
    private val arguments: List<Any?>
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
    private val returnExpr: Any?
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
    private val inputModel: InputModel,
    private val outputModel: OutputModel
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
    private val inputModel: InputModel,
    private val result: Any?,
) : ProgramExecutionResult {
    override fun toString(): String {
        return buildString {
            appendLine("================================================================")
            appendLine("Unsuccessful Execution")
            appendLine("----------------------------------------------------------------")
            appendLine(inputModel.toString())
            appendLine("----------------------------------------------------------------")
            appendLine(result)
            appendLine("================================================================")
        }
    }
}

class RNG(
    type: BasicType,
    seed: Long = 0,
    private val maxAttempts: Long = 10
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

        else -> throw IllegalStateException()
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
