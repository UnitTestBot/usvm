package org.usvm.machine

import org.usvm.*
import org.usvm.machine.utils.PyModel
import org.usvm.model.UModelBase

class PythonMockEvaluator(
    ctx: UPythonContext,
    private val baseMockEvaluator: UMockEvaluator,
    val mockSymbol: UMockSymbol<UAddressSort>,
    suggestedEvaluatedMockSymbol: UConcreteHeapRef? = null
): UMockEvaluator {
    val evaluatedMockSymbol = suggestedEvaluatedMockSymbol ?: ctx.provideRawConcreteHeapRef()
    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        val evaluatedValue = baseMockEvaluator.eval(symbol)

        if (symbol == mockSymbol && evaluatedValue is UConcreteHeapRef && evaluatedValue.address == 0) {
            @Suppress("unchecked_cast")
            return evaluatedMockSymbol as UExpr<Sort>
        }

        return evaluatedValue
    }
}

fun constructModelWithNewMockEvaluator(
    ctx: UPythonContext,
    oldModel: PyModel,
    mockSymbol: UMockSymbol<UAddressSort>,
    suggestedEvaluatedMockSymbol: UConcreteHeapRef? = null
): Pair<PyModel, UBoolExpr> {
    val newMockEvaluator = PythonMockEvaluator(ctx, oldModel.uModel.mocks, mockSymbol, suggestedEvaluatedMockSymbol)
    val newModel = UModelBase(
        ctx,
        oldModel.uModel.stack,
        oldModel.uModel.heap,
        oldModel.uModel.types,
        newMockEvaluator
    )
    val constraint = ctx.mkHeapRefEq(newMockEvaluator.mockSymbol, newMockEvaluator.evaluatedMockSymbol)
    return PyModel(newModel) to constraint
}