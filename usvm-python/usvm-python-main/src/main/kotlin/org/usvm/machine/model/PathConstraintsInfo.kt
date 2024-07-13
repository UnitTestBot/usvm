package org.usvm.machine.model

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KIntSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.collection.set.primitive.UInputSetReading
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsReading
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.PyContext
import org.usvm.machine.types.PythonType
import org.usvm.model.UModelBase
import org.usvm.regions.Region
import org.usvm.solver.UExprTranslator

data class PathConstraintsInfo(
    val setRefKeys: Set<UConcreteHeapRef>,
    val setIntKeys: Set<KInterpretedValue<KIntSort>>,
)

fun getPathConstraintsInfo(
    ctx: PyContext,
    ps: UPathConstraints<PythonType>,
    underlyingModel: UModelBase<PythonType>,
): PathConstraintsInfo {
    val visitor = ConstraintsVisitor(ctx)
    ps.constraints(visitor).toList()
    val refKeys = mutableSetOf<UConcreteHeapRef>()
    visitor.refKeys.forEach {
        val interpreted = underlyingModel.eval(it) as UConcreteHeapRef
        refKeys.add(interpreted)
    }
    val intKeys = mutableSetOf<KInterpretedValue<KIntSort>>(ctx.mkIntNum(0), ctx.mkIntNum(1))
    visitor.intKeys.forEach {
        val interpreted = underlyingModel.eval(it)
        intKeys.add(interpreted as KInterpretedValue<KIntSort>)
    }
    return PathConstraintsInfo(refKeys, intKeys)
}


@Suppress("unchecked_cast")
private class ConstraintsVisitor(ctx: PyContext) : UExprTranslator<PythonType, KIntSort>(ctx) {
    val refKeys: MutableSet<UHeapRef> = mutableSetOf()
    val intKeys: MutableSet<UExpr<KIntSort>> = mutableSetOf()

    override fun transform(expr: UInputRefSetWithInputElementsReading<PythonType>): UBoolExpr {
        refKeys.add(expr.elementRef)
        return super.transform(expr)
    }

    override fun <ElemSort : USort, Reg : Region<Reg>> transform(
        expr: UInputSetReading<PythonType, ElemSort, Reg>,
    ): UBoolExpr {
        if (expr.element.sort == ctx.intSort) {
            intKeys.add(expr.element as UExpr<KIntSort>)
        }
        return super.transform(expr)
    }
}
