package org.usvm.model

import io.ksmt.solver.KModel
import io.ksmt.utils.asExpr
import org.usvm.UExpr
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UMockEvaluator
import org.usvm.UMockSymbol
import org.usvm.USort
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
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val translator: UExprTranslator<*>,
) : UReadOnlyRegistersStack {
    private val uctx = translator.ctx

    override fun <Sort : USort> readRegister(
        index: Int,
        sort: Sort,
    ): UExpr<Sort> {
        val registerReading = uctx.mkRegisterReading(index, sort)
        val translated = translator.translate(registerReading)
        return model.eval(translated, isComplete = true).mapAddress(addressesMapping)
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
    private val model: KModel,
    private val addressesMapping: AddressesMapping,
    private val translator: UExprTranslator<*>,
) : UMockEvaluator {
    override fun <Sort : USort> eval(symbol: UMockSymbol<Sort>): UExpr<Sort> {
        require(symbol is UIndexedMethodReturnValue<*, Sort>)
        val translated = translator.translate(symbol)
        return model.eval(translated, isComplete = true).mapAddress(addressesMapping)
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
