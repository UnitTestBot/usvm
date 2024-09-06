package org.usvm.interpreter

import io.ksmt.expr.KBitVec16Value
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KBitVec64Value
import io.ksmt.expr.KBitVec8Value
import io.ksmt.expr.KConst
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
import org.jacodb.go.api.BasicType
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoType
import org.jacodb.go.api.NullType
import org.jacodb.go.api.SliceType
import org.usvm.GoContext
import org.usvm.NULL_ADDRESS
import org.usvm.UAddressSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.interpreter.GoInterpreter.Companion.logger
import org.usvm.isTrue
import org.usvm.memory.URegisterStackLValue
import org.usvm.memory.UWritableMemory
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.sizeSort
import org.usvm.state.GoMethodResult
import org.usvm.state.GoState

class GoTestInterpreter(
    private val ctx: GoContext,
) {
    fun resolve(state: GoState, method: GoMethod): ProgramExecutionResult = with(ctx) {
        val model = state.models.first()

        val inputScope = MemoryScope(ctx, model, model)
        val outputScope = MemoryScope(ctx, model, state.memory)

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
        private val model: UModelBase<GoType>,
        private val memory: UWritableMemory<GoType>,
    ) {
        fun convertExpr(expr: UExpr<out USort>, type: GoType): Any? = when (expr.sort) {
            is KBoolSort -> resolveBool(expr)
            is KBv8Sort -> resolveBv8(expr)
            is KBv16Sort -> resolveBv16(expr)
            is KBv32Sort -> resolveBv32(expr)
            is KBv64Sort -> resolveBv64(expr)
            is KFp32Sort -> resolveFp32(expr)
            is KFp64Sort -> resolveFp64(expr)
            is UAddressSort -> expr.asExpr(ctx.addressSort).let {
                when (type) {
                    is BasicType -> resolveString(it)
                    is SliceType -> resolveArray(it, type)
                    is NullType -> null
                    else -> Any()
                }
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

        fun resolveString(constString: UHeapRef): String {
            return (constString as KConst<*>).decl.name
        }

        fun resolveArray(array: UHeapRef, arrayType: SliceType): List<Any?>? = with(ctx) {
            if (array == mkConcreteHeapRef(NULL_ADDRESS) || array == nullRef) {
                return null
            }

            val elementType = arrayType.elementType
            val lengthUExpr = memory.readArrayLength(array, arrayType, sizeSort)
            val length = clipArrayLength(resolveSize(lengthUExpr))
            val sort = typeToSort(elementType)
            val result = (0 until length).map { idx ->
                val element = memory.readArrayIndex(array, mkSizeExpr(idx), arrayType, sort)
                convertExpr(element, elementType)
            }
            return result
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
