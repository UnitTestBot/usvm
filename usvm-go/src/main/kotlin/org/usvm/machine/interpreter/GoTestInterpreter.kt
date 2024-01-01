package org.usvm.machine.interpreter

import io.ksmt.expr.KBitVec16Value
import io.ksmt.expr.KBitVec32Value
import io.ksmt.expr.KBitVec64Value
import io.ksmt.expr.KBitVec8Value
import io.ksmt.expr.KFp32Value
import io.ksmt.expr.KFp64Value
import io.ksmt.utils.asExpr
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.bridge.GoBridge
import org.usvm.isTrue
import org.usvm.machine.GoContext
import org.usvm.machine.GoMethod
import org.usvm.machine.GoType
import org.usvm.machine.state.GoMethodResult
import org.usvm.machine.state.GoState
import org.usvm.machine.type.Type
import org.usvm.model.UModelBase

class GoTestInterpreter(
    private val bridge: GoBridge,
    private val ctx: GoContext,
) {
    fun resolve(state: GoState, method: GoMethod): ProgramExecutionResult {
        val model = state.models.first()

        val inputScope = InputScope(ctx, model)
        val methodInfo = bridge.methodInfo(method)

        val inputValues = methodInfo.parametersTypes.mapIndexed { idx, type ->
            val sort = ctx.typeToSort(type)
            val uExpr = model.stack.readRegister(idx, sort)
            inputScope.convertExpr(uExpr, type)
        }
        val inputModel = InputModel(inputValues)

        return if (state.isExceptional) {
            UnsuccessfulExecutionResult(inputModel)
        } else {
            val returnUExpr = state.methodResult as GoMethodResult.Success
            val returnExpr = returnUExpr.let { inputScope.convertExpr(it.value, methodInfo.returnType) }
            val outputModel = OutputModel(returnExpr)

            SuccessfulExecutionResult(inputModel, outputModel)
        }
    }

    private class InputScope(
        private val ctx: GoContext,
        private val model: UModelBase<GoType>,
    ) {
        fun convertExpr(expr: UExpr<USort>, type: Type): Any? =
            when (type) {
                Type.BOOL -> resolveBool(expr.asExpr(ctx.boolSort))
                Type.INT8, Type.UINT8 -> resolveBv8(expr)
                Type.INT16, Type.UINT16 -> resolveBv16(expr)
                Type.INT32, Type.UINT32 -> resolveBv32(expr)
                Type.INT64, Type.UINT64 -> resolveBv64(expr)
                Type.FLOAT32 -> resolveFp32(expr)
                Type.FLOAT64 -> resolveFp64(expr)
                else -> null
            }

        fun resolveBool(expr: UExpr<UBoolSort>) = model.eval(expr).asExpr(ctx.boolSort).isTrue
        fun resolveBv8(expr: UExpr<USort>) = (model.eval(expr) as KBitVec8Value).byteValue
        fun resolveBv16(expr: UExpr<USort>) = (model.eval(expr) as KBitVec16Value).shortValue
        fun resolveBv32(expr: UExpr<USort>) = (model.eval(expr) as KBitVec32Value).intValue
        fun resolveBv64(expr: UExpr<USort>) = (model.eval(expr) as KBitVec64Value).longValue
        fun resolveFp32(expr: UExpr<USort>) = (model.eval(expr) as KFp32Value).value
        fun resolveFp64(expr: UExpr<USort>) = (model.eval(expr) as KFp64Value).value
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
) : ProgramExecutionResult {
    override fun toString(): String {
        return buildString {
            appendLine("================================================================")
            appendLine("Unsuccessful Execution")
            appendLine("----------------------------------------------------------------")
            appendLine(inputModel.toString())
            appendLine("================================================================")
        }
    }
}