package org.usvm.machine

import org.usvm.*
import org.usvm.model.UModelBase

class PythonMockEvaluator(
    ctx: UPythonContext,
    private val baseMockEvaluator: UMockEvaluator,
    private val mockSymbol: UMockSymbol<UAddressSort>
): UMockEvaluator {
    private val evaluatedMockSymbol = ctx.provideRawConcreteHeapRef()
    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        if (symbol == mockSymbol) {
            @Suppress("unchecked_cast")
            return evaluatedMockSymbol as UExpr<Sort>
        }
        return baseMockEvaluator.eval(symbol)
    }
}

fun constructModelWithNewMockEvaluator(ctx: UPythonContext, oldModel: PyModel, mockSymbol: UMockSymbol<UAddressSort>): PyModel {
    val newMockEvaluator = PythonMockEvaluator(ctx, oldModel.uModel.mocks, mockSymbol)
    val newModel = UModelBase(
        ctx,
        oldModel.uModel.stack,
        oldModel.uModel.heap,
        oldModel.uModel.types,
        newMockEvaluator
    )
    return PyModel(newModel)
}