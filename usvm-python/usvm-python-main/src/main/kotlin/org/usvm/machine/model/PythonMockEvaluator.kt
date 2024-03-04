package org.usvm.machine.model

import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.types.PythonType
import org.usvm.machine.PyContext
import org.usvm.model.UModelBase

class PythonMockEvaluator(
    ctx: PyContext,
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
    ctx: PyContext,
    oldModel: PyModel,
    mockSymbol: UMockSymbol<UAddressSort>,
    ps: UPathConstraints<PythonType>,
    suggestedEvaluatedMockSymbol: UConcreteHeapRef? = null,
    useOldPossibleRefs: Boolean = false
): Pair<PyModel, UBoolExpr> {
    val newMockEvaluator = PythonMockEvaluator(ctx, oldModel.mocker, mockSymbol, suggestedEvaluatedMockSymbol)
    val suggestedPsInfo = if (useOldPossibleRefs) {
        oldModel.psInfo
    } else {
        null
    }
    val newModel = UModelBase(
        ctx,
        oldModel.stack,
        oldModel.types,
        newMockEvaluator,
        oldModel.regions,
        oldModel.nullRef
    ).toPyModel(ctx, ps, suggestedPsInfo)
    val constraint = ctx.mkHeapRefEq(newMockEvaluator.mockSymbol, newMockEvaluator.evaluatedMockSymbol)
    return newModel to constraint
}