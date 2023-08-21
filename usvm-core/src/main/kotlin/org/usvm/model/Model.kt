package org.usvm.model

import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UComposer
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UMockEvaluator
import org.usvm.USort
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.UReadOnlyRegistersStack
import org.usvm.memory.URegisterStackId
import org.usvm.memory.UWritableMemory
import org.usvm.sampleUValue

interface UModel {
    fun <Sort : USort> eval(expr: UExpr<Sort>): UExpr<Sort>
}

// TODO: Eval visitor

/**
 * Consists of decoded components and allows to evaluate any expression. Evaluation is done via generic composition.
 * Evaluated expressions are cached within [UModelBase] instance.
 * If a symbol from an expression not found inside the model, components return the default value
 * of the correct sort.
 */
open class UModelBase<Type>(
    ctx: UContext,
    override val stack: UReadOnlyRegistersStack,
    override val types: UTypeModel<Type>,
    override val mocker: UMockEvaluator,
    internal val regions: Map<UMemoryRegionId<*, *>, UMemoryRegion<*, *>>,
    internal val nullRef: UConcreteHeapRef,
) : UModel, UWritableMemory<Type> {
    private val composer = UComposer(ctx, this)

    /**
     * The evaluator supports only expressions with symbols inheriting [org.usvm.USymbol].
     *
     * For instance, raw [io.ksmt.expr.KConst] cannot occur in the [expr], only as a result of
     * a reading of some sort, a mock symbol, etc.
     */
    override fun <Sort : USort> eval(expr: UExpr<Sort>): UExpr<Sort> =
        composer.compose(expr)

    override fun <Key, Sort : USort> getRegion(regionId: UMemoryRegionId<Key, Sort>): UReadOnlyMemoryRegion<Key, Sort> {
        if (regionId is URegisterStackId) {
            return stack.uncheckedCast()
        }
        return regions[regionId]?.uncheckedCast() ?: DefaultRegion(regionId)
    }

    override fun nullRef(): UHeapRef = nullRef

    override fun toWritableMemory(): UWritableMemory<Type> = this

    override fun <Key, Sort : USort> setRegion(
        regionId: UMemoryRegionId<Key, Sort>,
        newRegion: UMemoryRegion<Key, Sort>
    ) {
        error("Illegal operation for a model")
    }

    override fun <Key, Sort : USort> write(lvalue: ULValue<Key, Sort>, rvalue: UExpr<Sort>, guard: UBoolExpr) {
        error("Illegal operation for a model")
    }

    override fun alloc(type: Type): UConcreteHeapRef {
        error("Illegal operation for a model")
    }

    private class DefaultRegion<Key, Sort : USort>(
        private val regionId: UMemoryRegionId<Key, Sort>
    ) : UReadOnlyMemoryRegion<Key, Sort> {
        override fun read(key: Key): UExpr<Sort> {
            return regionId.sort.sampleUValue()
        }
    }
}
