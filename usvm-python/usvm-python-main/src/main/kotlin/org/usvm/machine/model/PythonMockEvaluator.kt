package org.usvm.machine.model

import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UMockEvaluator
import org.usvm.UMockSymbol
import org.usvm.USort
import org.usvm.machine.PyContext
import org.usvm.model.UModelBase

class PythonMockEvaluator(
    private val ctx: PyContext,
    private var baseMockEvaluator: UMockEvaluator,
    val mockSymbol: UMockSymbol<UAddressSort>,
    suggestedEvaluatedMockSymbol: UConcreteHeapRef? = null,
) : UMockEvaluator {
    val evaluatedMockSymbol = suggestedEvaluatedMockSymbol ?: ctx.provideRawConcreteHeapRef()
    private val mockTable = mutableMapOf<UMockSymbol<UAddressSort>, UHeapRef>()

    init {
        val givenEvaluator = baseMockEvaluator
        if (givenEvaluator is PythonMockEvaluator) {
            baseMockEvaluator = givenEvaluator.baseMockEvaluator
            givenEvaluator.mockTable.forEach { (m, res) -> mockTable[m] = res }
        }
        mockTable[mockSymbol] = evaluatedMockSymbol
    }

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        requireNotNull(symbol.sort == ctx.addressSort)

        val evaluatedValue = baseMockEvaluator.eval(symbol)

        val valueFromTable =
            @Suppress("unchecked_cast")
            mockTable[symbol as UMockSymbol<UAddressSort>]

        if (valueFromTable != null && evaluatedValue is UConcreteHeapRef && evaluatedValue.address == 0) {
            @Suppress("unchecked_cast")
            return valueFromTable as UExpr<Sort>
        }

        return evaluatedValue
    }
}

fun constructModelWithNewMockEvaluator(
    ctx: PyContext,
    oldModel: PyModel,
    mockSymbol: UMockSymbol<UAddressSort>,
    info: GivenPathConstraintsInfo,
    suggestedEvaluatedMockSymbol: UConcreteHeapRef? = null,
): Pair<PyModel, UBoolExpr> {
    val newMockEvaluator = PythonMockEvaluator(ctx, oldModel.mocker, mockSymbol, suggestedEvaluatedMockSymbol)
    val newModel = UModelBase(
        ctx,
        oldModel.stack,
        oldModel.types,
        newMockEvaluator,
        oldModel.regions,
        oldModel.nullRef
    ).toPyModel(ctx, info)
    val constraint = ctx.mkHeapRefEq(newMockEvaluator.mockSymbol, newMockEvaluator.evaluatedMockSymbol)
    return newModel to constraint
}
