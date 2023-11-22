package org.usvm.model

import io.ksmt.utils.asExpr
import org.usvm.UExpr
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UMockEvaluator
import org.usvm.UMockSymbol
import org.usvm.USort
import org.usvm.UTrackedMockSymbol
import org.usvm.memory.UReadOnlyRegistersStack
import org.usvm.solver.UExprTranslator
import org.usvm.uctx


/**
 * A lazy model for registers. Firstly, searches for translated symbol, then evaluates it in [model].
 *
 * @param model to decode from. It has to be detached.
 * @param translator an expression translator used for encoding constraints.
 * Provides translated symbolic constants for registers readings.
 */
class ULazyRegistersStackModel(
    private val model: UModelEvaluator<*>,
    private val translator: UExprTranslator<*, *>,
) : UReadOnlyRegistersStack {
    override fun <Sort : USort> readRegister(
        index: Int,
        sort: Sort,
    ): UExpr<Sort> {
        val registerReading = translator.ctx.mkRegisterReading(index, sort)
        val translated = translator.translate(registerReading)
        return model.evalAndComplete(translated)
    }
}

/**
 * A lazy model for an indexed mocker. Firstly, searches for translated symbol, then evaluates it in [model].
 *
 * @param model to decode from. It has to be detached.
 * @param translator an expression translator used for encoding constraints.
 * Provides translated symbolic constants for mock symbols.
 */
class ULazyIndexedMockModel(
    private val model: UModelEvaluator<*>,
    private val translator: UExprTranslator<*, *>,
) : UMockEvaluator {
    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        require(symbol is UIndexedMethodReturnValue<*, Sort> || symbol is UTrackedMockSymbol<Sort>) {
            "Unexpected symbol $symbol found"
        }

        val translated = translator.translate(symbol)
        return model.evalAndComplete(translated)
    }
}

/**
 * If [this] value is an instance of address expression, returns
 * an expression with a corresponding concrete address, otherwise
 * returns [this] unchanched.
 */
fun <S : USort> UExpr<S>.mapAddress(
    addressesMapping: AddressesMapping,
): UExpr<S> = if (sort == uctx.addressSort) {
    addressesMapping.getValue(asExpr(uctx.addressSort)).asExpr(sort)
} else {
    this
}
