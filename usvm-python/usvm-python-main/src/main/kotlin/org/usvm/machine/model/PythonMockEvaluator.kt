package org.usvm.machine.model

import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.UPythonContext
import org.usvm.machine.symbolicobjects.PreallocatedObjects
import org.usvm.machine.utils.PyModelWrapper
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
    oldModel: PyModelWrapper,
    mockSymbol: UMockSymbol<UAddressSort>,
    typeSystem: PythonTypeSystem,
    ps: UPathConstraints<PythonType>,
    preallocatedObjects: PreallocatedObjects,
    suggestedEvaluatedMockSymbol: UConcreteHeapRef? = null
): Pair<PyModelWrapper, UBoolExpr> {
    val newMockEvaluator = PythonMockEvaluator(ctx, oldModel.uModel.mocker, mockSymbol, suggestedEvaluatedMockSymbol)
    val newModel = UModelBase(
        ctx,
        oldModel.uModel.stack,
        oldModel.uModel.types,
        newMockEvaluator,
        oldModel.uModel.regions,
        oldModel.uModel.nullRef
    ).toPyModel(ctx, typeSystem, ps, preallocatedObjects)
    val constraint = ctx.mkHeapRefEq(newMockEvaluator.mockSymbol, newMockEvaluator.evaluatedMockSymbol)
    return PyModelWrapper(newModel) to constraint
}