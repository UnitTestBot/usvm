package org.usvm.interpreter

import org.usvm.language.Expr
import org.usvm.language.ProgramException
import org.usvm.language.SampleType

sealed interface ProgramExecutionResult

class InputModel(
    val argumentExprs: List<Expr<SampleType>>
) {
    override fun toString(): String {
        return buildString {
            appendLine("InputModel")
            val argumentsRepr = argumentExprs.joinToString(",\n\t", "Arguments [\n\t", "\n]")
            appendLine(argumentsRepr.prependIndent("\t"))
        }
    }
}

class OutputModel(
    val returnExpr: Expr<SampleType>?
) {
    override fun toString(): String {
        return buildString {
            appendLine("OutputModel")
            val returnRepr = "Return [\n\t$returnExpr\n]"
            appendLine(returnRepr.prependIndent("\t"))
        }
    }
}

class SuccessfulExecutionResult(
    val inputModel: InputModel,
    val outputModel: OutputModel
) : ProgramExecutionResult {
    override fun toString(): String {
        return buildString {
            appendLine("================================================================")
            appendLine("Successful Execution")
            appendLine("----------------------------------------------------------------")
            appendLine(inputModel.toString().prependIndent("\t"))
            appendLine("----------------------------------------------------------------")
            appendLine(outputModel.toString().prependIndent("\t"))
            appendLine("================================================================")
        }
    }
}

class UnsuccessfulExecutionResult(
    val inputModel: InputModel,
    val exception: ProgramException
) : ProgramExecutionResult {
    override fun toString(): String {
        return buildString {
            appendLine("================================================================")
            appendLine("Unsuccessful Execution")
            appendLine("----------------------------------------------------------------")
            appendLine(inputModel.toString().prependIndent("\t"))
            appendLine("----------------------------------------------------------------")
            appendLine(exception.toString().prependIndent("\t"))
            appendLine("================================================================")
        }
    }
}