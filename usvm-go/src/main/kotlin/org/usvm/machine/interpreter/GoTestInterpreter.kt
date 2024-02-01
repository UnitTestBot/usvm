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
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.api.readField
import org.usvm.bridge.GoBridge
import org.usvm.collection.map.primitive.UMapEntryLValue
import org.usvm.collection.set.primitive.setEntries
import org.usvm.isTrue
import org.usvm.logger
import org.usvm.machine.GoContext
import org.usvm.machine.GoMethod
import org.usvm.machine.state.GoMethodResult
import org.usvm.machine.state.GoState
import org.usvm.machine.type.GoType
import org.usvm.machine.type.GoSort
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.UWritableMemory
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.sizeSort

class GoTestInterpreter(
    private val ctx: GoContext,
    private val bridge: GoBridge,
) {
    fun resolve(state: GoState, method: GoMethod): ProgramExecutionResult {
        val model = state.models.first()

        val inputScope = MemoryScope(ctx, bridge, model, model)
        val outputScope = MemoryScope(ctx, bridge, model, state.memory)
        val methodInfo = bridge.methodInfo(method)

        val inputValues = methodInfo.parametersTypes.mapIndexed { idx, type ->
            val sort = ctx.mapSort(bridge.typeToSort(type))
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
        private val bridge: GoBridge,
        private val model: UModelBase<GoType>,
        private val memory: UWritableMemory<GoType>,
    ) {
        fun convertExpr(expr: UExpr<out USort>, type: GoType): Any? {
            val sort = bridge.typeToSort(type)
            return when (sort) {
                GoSort.BOOL -> resolveBool(expr.asExpr(ctx.boolSort))
                GoSort.INT8, GoSort.UINT8 -> resolveBv8(expr)
                GoSort.INT16, GoSort.UINT16 -> resolveBv16(expr)
                GoSort.INT32, GoSort.UINT32 -> resolveBv32(expr)
                GoSort.INT64, GoSort.UINT64 -> resolveBv64(expr)
                GoSort.FLOAT32 -> resolveFp32(expr)
                GoSort.FLOAT64 -> resolveFp64(expr)
                GoSort.ARRAY, GoSort.SLICE -> resolveArray(expr.asExpr(ctx.addressSort), type)
                GoSort.MAP -> resolveMap(expr.asExpr(ctx.addressSort), type)
                GoSort.STRUCT -> resolveStruct(expr.asExpr(ctx.addressSort), type)
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

        fun resolveArray(expr: UHeapRef, type: GoType): List<Any?>? {
            val array = model.eval(expr)
            if (array == ctx.mkConcreteHeapRef(NULL_ADDRESS)) {
                return null
            }

            val arrayType = bridge.typeHash(type)
            val elementType = bridge.arrayElementType(type)
            val lengthUExpr = memory.readArrayLength(array, arrayType, ctx.sizeSort)
            val length = clipArrayLength(convertExpr(lengthUExpr, elementType) as Int)
            val result = (0 until length).map { idx ->
                val sort = ctx.mapSort(bridge.typeToSort(elementType))
                val element = memory.readArrayIndex(array, ctx.mkSizeExpr(idx), arrayType, sort)
                convertExpr(element, elementType)
            }
            return result
        }

        fun resolveMap(expr: UHeapRef, type: GoType): Map<Any?, Any?>? {
            val map = model.eval(expr)
            if (map == ctx.mkConcreteHeapRef(NULL_ADDRESS)) {
                return null
            }

            val mapType = bridge.typeHash(type)
            val (keyType, valueType) = bridge.mapKeyValueTypes(type)
            val keySort = ctx.mapSort(bridge.typeToSort(keyType))
            val valueSort = ctx.mapSort(bridge.typeToSort(valueType))
            val entries = memory.setEntries(map, mapType, keySort, USizeExprKeyInfo()).entries

            return entries.associate { entry ->
                val key = entry.setElement
                val value = memory.read(UMapEntryLValue(key.sort, valueSort, map, key, mapType, USizeExprKeyInfo()))
                convertExpr(key, keyType) to convertExpr(value, valueType)
            }
        }

        fun resolveStruct(expr: UHeapRef, type: GoType): List<Any?>? {
            val struct = model.eval(expr)
            if (struct == ctx.mkConcreteHeapRef(NULL_ADDRESS)) {
                return null
            }

            val types = bridge.structFieldTypes(type)
            return List(types.size) {
                val sort = ctx.mapSort(bridge.typeToSort(types[it]))
                convertExpr(memory.readField(struct, it, sort), types[it])
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