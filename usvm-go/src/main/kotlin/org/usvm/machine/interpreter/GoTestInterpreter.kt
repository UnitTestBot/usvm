package org.usvm.machine.interpreter

import io.ksmt.expr.KBitVec16Value
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KBitVec64Value
import io.ksmt.expr.KBitVec8Value
import io.ksmt.expr.KFp32Value
import io.ksmt.expr.KFp64Value
import io.ksmt.utils.asExpr
import org.usvm.NULL_ADDRESS
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.collection.ObjectMapCollectionApi.symbolicObjectMapAnyKey
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.api.readField
import org.usvm.bridge.GoBridge
import org.usvm.collection.map.length.UMapLengthLValue
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.primitive.setEntries
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.set.ref.refSetEntries
import org.usvm.isTrue
import org.usvm.logger
import org.usvm.machine.GoContext
import org.usvm.machine.GoMethod
import org.usvm.machine.state.GoMethodResult
import org.usvm.machine.state.GoState
import org.usvm.machine.type.GoType
import org.usvm.machine.type.GoSort
import org.usvm.memory.ULValue
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.sampleUValue
import org.usvm.sizeSort
import kotlin.random.Random
import kotlin.random.nextUInt
import kotlin.random.nextULong

class GoTestInterpreter(
    private val ctx: GoContext,
    private val bridge: GoBridge,
) {
    fun resolve(state: GoState, method: GoMethod): ProgramExecutionResult = with(ctx) {
        val model = state.models.first()

        val inputScope = MemoryScope(ctx, state, bridge, model, model)
        val outputScope = MemoryScope(ctx, state, bridge, model, state.memory)
        val methodInfo = getMethodInfo(method)

        val inputValues = methodInfo.parametersTypes.mapIndexed { idx, type ->
            val sort = mapSort(bridge.typeToSort(type))
            val expr = model.read(URegisterStackLValue(sort, idx))
            inputScope.convertExpr(expr, type)
        }
        val inputModel = InputModel(inputValues)

        return if (state.isExceptional) {
            UnsuccessfulExecutionResult(inputModel, (state.methodResult as GoMethodResult.Panic).value)
        } else {
            val result = state.methodResult as GoMethodResult.Success
            val expr = result.let { outputScope.convertExpr(it.value, methodInfo.returnType) }
            val outputModel = OutputModel(expr)

            SuccessfulExecutionResult(inputModel, outputModel)
        }
    }

    private class MemoryScope(
        private val ctx: GoContext,
        private val state: GoState,
        private val bridge: GoBridge,
        private val model: UModelBase<GoType>,
        private val memory: UWritableMemory<GoType>,
    ) {
        fun convertExpr(expr: UExpr<out USort>, type: GoType): Any? = with(ctx) {
            val sort = bridge.typeToSort(type)
            return when (sort) {
                GoSort.BOOL -> resolveBool(expr.asExpr(boolSort))
                GoSort.INT8, GoSort.UINT8 -> resolveBv8(expr)
                GoSort.INT16, GoSort.UINT16 -> resolveBv16(expr)
                GoSort.INT32, GoSort.UINT32 -> resolveBv32(expr)
                GoSort.INT64, GoSort.UINT64 -> resolveBv64(expr)
                GoSort.FLOAT32 -> resolveFp32(expr)
                GoSort.FLOAT64 -> resolveFp64(expr)
                GoSort.STRING -> resolveString(expr.asExpr(addressSort), type)
                GoSort.ARRAY, GoSort.SLICE -> resolveArray(expr.asExpr(addressSort), type)
                GoSort.MAP -> resolveMap(expr.asExpr(addressSort), type)
                GoSort.STRUCT -> resolveStruct(expr.asExpr(addressSort), type)
                GoSort.TUPLE -> resolveTuple(expr.asExpr(addressSort), type)
                else -> null
            }
        }

        fun resolveBool(expr: UExpr<UBoolSort>) = model.eval(expr).asExpr(ctx.boolSort).isTrue

        fun resolveBv8(expr: UExpr<out USort>) = (model.eval(expr) as KBitVec8Value).byteValue

        fun resolveBv16(expr: UExpr<out USort>) = (model.eval(expr) as KBitVec16Value).shortValue

        fun resolveBv32(expr: UExpr<out USort>) = (model.eval(expr) as KBitVec32Value).intValue

        fun resolveBv64(expr: UExpr<out USort>) = (model.eval(expr) as KBitVec64Value).longValue

        fun resolveFp32(expr: UExpr<out USort>) = (model.eval(expr) as KFp32Value).value

        fun resolveFp64(expr: UExpr<out USort>) = (model.eval(expr) as KFp64Value).value

        fun resolveSize(expr: UExpr<out USort>) = (model.eval(expr) as KBitVec32Value).numberValue

        fun resolveString(string: UHeapRef, type: GoType): String? {
            val integers = resolveArray(string, type) ?: return null
            return "\"" + String(integers.map { (it as Int).toChar() }.toCharArray()) + "\""
        }

        fun resolveArray(array: UHeapRef, type: GoType): List<Any?>? = with(ctx) {
            if (array == mkConcreteHeapRef(NULL_ADDRESS)) {
                return null
            }

            val arrayType = bridge.typeHash(type)
            val elementType = bridge.arrayElementType(type)
            val lengthUExpr = memory.readArrayLength(array, arrayType, sizeSort)
            val length = clipArrayLength(resolveSize(lengthUExpr))
            val sort = mapSort(bridge.typeToSort(elementType))
            val result = (0 until length).map { idx ->
                val element = memory.readArrayIndex(array, mkSizeExpr(idx), arrayType, sort)
                convertExpr(element, elementType)
            }
            return result
        }

        fun resolveMap(map: UHeapRef, type: GoType): Map<Any?, Any?>? = with(ctx) {
            if (map == mkConcreteHeapRef(NULL_ADDRESS)) {
                return null
            }

            val mapType = bridge.typeHash(type)
            val (keyType, valueType) = bridge.mapKeyValueTypes(type)
            val keySort = mapSort(bridge.typeToSort(keyType))
            val valueSort = mapSort(bridge.typeToSort(valueType))

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
                val rng = RNG(bridge.typeToSort(keyType))
                val diff = length - result.size
                val entries = Array(diff) {
                    val key = if (isRefSet) {
                        convertExpr(state.symbolicObjectMapAnyKey(map, mapType), keyType)
                    } else {
                        rng.generateUniqueMapKey(result)
                    }
                    val value = convertExpr(valueSort.sampleUValue(), valueType)
                    key to value
                }
                result.putAll(entries)
            }

            return result
        }

        fun resolveStruct(struct: UHeapRef, type: GoType): List<Any?>? = with(ctx) {
            if (struct == mkConcreteHeapRef(NULL_ADDRESS)) {
                return null
            }

            val types = bridge.structFieldTypes(type)
            return List(types.size) {
                val sort = mapSort(bridge.typeToSort(types[it]))
                convertExpr(memory.readField(struct, it, sort), types[it])
            }
        }

        fun resolveTuple(tuple: UHeapRef, type: GoType): List<Any?>? = with(ctx) {
            if (tuple == mkConcreteHeapRef(NULL_ADDRESS)) {
                return null
            }

            val types = bridge.tupleTypes(type)
            return List(types.size) {
                val sort = mapSort(bridge.typeToSort(types[it]))
                convertExpr(memory.readField(tuple, it, sort), types[it])
            }
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
    private val result: Any,
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
    sort: GoSort,
    seed: Long = 0,
    private val maxAttempts: Long = 10
) {
    private val random = Random(seed)

    private val generate: () -> Any = when (sort) {
        GoSort.INT8 -> {
            { random.nextBytes(1).first() }
        }

        GoSort.UINT8 -> {
            { random.nextBytes(1).first().toUByte() }
        }

        GoSort.INT16 -> {
            { random.nextInt().toShort() }
        }

        GoSort.UINT16 -> {
            { random.nextInt().toUShort() }
        }

        GoSort.INT32 -> {
            { random.nextInt() }
        }

        GoSort.UINT32 -> {
            { random.nextUInt() }
        }

        GoSort.INT64 -> {
            { random.nextLong() }
        }

        GoSort.UINT64 -> {
            { random.nextULong() }
        }

        GoSort.FLOAT32 -> {
            { random.nextFloat() }
        }

        GoSort.FLOAT64 -> {
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