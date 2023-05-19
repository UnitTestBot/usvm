package org.usvm.interpreter.accessors

import org.usvm.UState
import org.usvm.interpreter.StepScope
import kotlin.reflect.KMutableProperty1

interface UReturnOperations<T : UState<Type, Field, Method, Statement>, Type, Field, Method, Statement, MethodResult> {
    fun T.exitMethod(value: MethodResult)
    fun StepScope<T, Type>.processCall(
        onNoCall: () -> Unit,
        onSuccess: (MethodResult) -> Unit,
    )
}

class UReturnOperationsImpl<T : UState<Type, Field, Method, Statement>, Type, Field, Method, Statement, MethodResult>(
    private val baseOperations: UBaseOperations<T, Type, Field, Method, Statement>,
    private val resultProperty: KMutableProperty1<T, MethodResult?>,
) : UReturnOperations<T, Type, Field, Method, Statement, MethodResult> {
    override fun T.exitMethod(value: MethodResult) {
        resultProperty.set(this, value)
        with(baseOperations) { popFrame() }
    }

    override fun StepScope<T, Type>.processCall(
        onNoCall: () -> Unit,
        onSuccess: (MethodResult) -> Unit,
    ) {
        val retRegister = calcOnState {
            val retRegister = resultProperty.get(this)
            resultProperty.set(this, null)
            retRegister
        }

        when (retRegister) {
            null -> onNoCall()
            else -> onSuccess(retRegister)
        }
    }
}