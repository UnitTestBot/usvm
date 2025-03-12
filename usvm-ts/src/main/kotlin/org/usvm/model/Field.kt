package org.usvm.model

import org.jacodb.ets.model.EtsModifiers

interface TsField {
    val signature: TsFieldSignature
    val enclosingClass: TsClass?
}

class TsFieldImpl(
    override val signature: TsFieldSignature,
    val modifiers: EtsModifiers = EtsModifiers.EMPTY,
    val isOptional: Boolean = false,  // '?'
    val isDefinitelyAssigned: Boolean = false, // '!'
) : TsField {
    override var enclosingClass: TsClass? = null

    override fun toString(): String {
        return signature.toString()
    }
}
