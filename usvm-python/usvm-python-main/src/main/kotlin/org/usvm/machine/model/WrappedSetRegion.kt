package org.usvm.machine.model

import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsReading
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.constraints.UPathConstraints
import org.usvm.language.types.PythonType
import org.usvm.machine.UPythonContext
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.model.UModelBase
import org.usvm.solver.UExprTranslator

class WrappedSetRegion<SetType>(
    private val ctx: UPythonContext,
    private val region: UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort>,
    private val keys: Set<UConcreteHeapRef>
): UReadOnlyMemoryRegion<URefSetEntryLValue<SetType>, UBoolSort> {
    override fun read(key: URefSetEntryLValue<SetType>): UExpr<UBoolSort> {
        if (key.setElement !in keys) {
            return ctx.falseExpr
        }
        return region.read(key)
    }

    companion object {
        fun constructKeys(
            ctx: UPythonContext,
            ps: UPathConstraints<PythonType>,
            underlyingModel: UModelBase<PythonType>
        ): Set<UConcreteHeapRef> {
            val visitor = VisitorForSetRegion(ctx)
            ps.constraints(visitor).toList()
            val keys = mutableSetOf<UConcreteHeapRef>()  // TODO: add True and False
            visitor.keys.forEach {
                val interpreted = underlyingModel.eval(it) as UConcreteHeapRef
                keys.add(interpreted)
            }
            return keys
        }
    }
}

private class VisitorForSetRegion(ctx: UPythonContext): UExprTranslator<PythonType, KIntSort>(ctx) {
    val keys: MutableSet<UHeapRef> = mutableSetOf()
    override fun transform(expr: UAllocatedRefSetWithInputElementsReading<PythonType>): UBoolExpr {
        keys.add(expr.elementRef)
        return super.transform(expr)
    }

    override fun transform(expr: UInputRefSetWithInputElementsReading<PythonType>): UBoolExpr {
        keys.add(expr.elementRef)
        return super.transform(expr)
    }
}