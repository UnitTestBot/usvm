package org.usvm.interpreter

import org.usvm.ApplicationGraph
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.interpreter.accessors.UBaseOperations
import org.usvm.interpreter.accessors.UBaseOperationsImpl
import org.usvm.interpreter.accessors.UReturnOperations
import org.usvm.interpreter.accessors.UReturnOperationsImpl
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.ProgramException
import org.usvm.language.SampleType
import org.usvm.language.Stmt

class SampleStateOperations(
    private val baseOperations: UBaseOperations<SampleState, SampleType, Field<*>, Method<*>, Stmt>,
    private val returnOperations: UReturnOperations<SampleState, SampleType, Field<*>, Method<*>, Stmt, UExpr<out USort>>,
) : UBaseOperations<SampleState, SampleType, Field<*>, Method<*>, Stmt> by baseOperations,
    UReturnOperations<SampleState, SampleType, Field<*>, Method<*>, Stmt, UExpr<out USort>> by returnOperations {

    fun SampleState.throwException(exception: ProgramException) {
        exceptionRegister = exception
        popFrame()
    }

    fun SampleState.returnValue(value: UExpr<out USort>?) {
        returnRegister = value
        popFrame()
    }

    inline fun SampleStepScope.processCall(
        onNoCall: () -> Unit,
        onSuccess: (UExpr<out USort>?) -> Unit,
    ) {
        val retRegister = calcOnState {
            val retRegister = returnRegister
            returnRegister = null
            retRegister
        }

        when (retRegister) {
            null -> onNoCall()
            else -> onSuccess(retRegister)
        }
    }

    companion object {
        fun create(
            applicationGraph: ApplicationGraph<Method<*>, Stmt>,
            methodToArgumentsCount: (Method<*>) -> Int,
            methodToLocalsCount: (Method<*>) -> Int,
        ): SampleStateOperations {
            val baseOperations = UBaseOperationsImpl<SampleState, SampleType, Field<*>, Method<*>, Stmt>(
                applicationGraph,
                methodToArgumentsCount,
                methodToLocalsCount
            )
            val returnOperations = UReturnOperationsImpl(baseOperations, SampleState::returnRegister)
            return SampleStateOperations(baseOperations, returnOperations)
        }
    }
}
