package org.usvm.collection.set

import io.ksmt.decl.KFuncDecl
import io.ksmt.expr.KExpr
import io.ksmt.expr.KFunctionApp
import io.ksmt.solver.KModel
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.uncheckedCast
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.isTrue
import org.usvm.model.FunctionAppCollector
import org.usvm.model.UMemory2DArray
import org.usvm.model.mapAddress

abstract class USetCollectionDecoder<ElementSort : USort> {
    abstract val inputFunction: KFuncDecl<KBoolSort>

    private val appCollector by lazy {
        FunctionAppCollector(inputFunction.ctx, inputFunction)
    }

    // todo: think about a better way of set keys retrieval to avoid traversing all the assertions.
    fun decodeCollection(
        model: KModel,
        mapping: Map<UHeapRef, UConcreteHeapRef>,
        assertions: List<KExpr<KBoolSort>>,
    ): UMemory2DArray<UAddressSort, ElementSort, UBoolSort> {
        if (model.interpretation(inputFunction) == null) {
            // Set is free in model -> return an empty set
            return UMemory2DArray(persistentMapOf(), constValue = inputFunction.ctx.falseExpr)
        }

        val usedSetKeys = hashSetOf<KFunctionApp<KBoolSort>>()
        assertions.flatMapTo(usedSetKeys) { appCollector.applyVisitor(it) }

        val entries = mutableMapOf<USymbolicSetElement<ElementSort>, UBoolExpr>()
        for (key in usedSetKeys) {
            val keyInSet = model.eval(key, isComplete = false)
            if (!keyInSet.isTrue) continue

            val (rawSetRef, rawElement) = key.args
            val setRef: UHeapRef = rawSetRef.uncheckedCast()
            val element: UExpr<ElementSort> = rawElement.uncheckedCast()

            val setRefModel = model.eval(setRef, isComplete = true).mapAddress(mapping)
            val elementModel = model.eval(element, isComplete = true).mapAddress(mapping)

            entries[setRefModel to elementModel] = inputFunction.ctx.trueExpr
        }

        return UMemory2DArray(entries.toPersistentMap(), constValue = inputFunction.ctx.falseExpr)
    }
}
