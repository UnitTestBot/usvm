package org.usvm.model

import io.ksmt.utils.asExpr
import io.ksmt.utils.sampleValue
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UMockEvaluator
import org.usvm.UMockSymbol
import org.usvm.USort
import org.usvm.memory.UReadOnlyRegistersStack
import org.usvm.uctx

/**
 * An eager model for registers that stores mapping
 * from mock symbols to evaluated expressions.
 */
class URegistersStackEagerModel(
    private val nullRef: UConcreteHeapRef,
    private val registers: Map<Int, UExpr<out USort>>
) : UReadOnlyRegistersStack {
    override fun <Sort : USort> readRegister(
        index: Int,
        sort: Sort,
    ): UExpr<Sort> = registers
        .getOrElse(index) { sort.sampleValue().nullAddress(nullRef) } // sampleValue here is important
        .asExpr(sort)
}

/**
 * An eager model for an indexed mocker that stores mapping
 * from mock symbols and invocation indices to expressions.
 */
class UIndexedMockEagerModel<Method>(
    private val nullRef: UConcreteHeapRef,
    private val values: Map<Pair<*, Int>, UExpr<*>>,
) : UMockEvaluator {

    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        require(symbol is UIndexedMethodReturnValue<*, Sort>)

        val sort = symbol.sort

        @Suppress("UNCHECKED_CAST")
        val key = symbol.method as Method to symbol.callIndex

        // sampleValue here is important
        return values.getOrDefault(key, sort.sampleValue().nullAddress(nullRef)).asExpr(sort)
    }
}

fun <T : USort> UExpr<T>.nullAddress(nullRef: UConcreteHeapRef): UExpr<T> =
    if (this == uctx.nullRef) {
        nullRef.asExpr(sort)
    } else {
        this
    }