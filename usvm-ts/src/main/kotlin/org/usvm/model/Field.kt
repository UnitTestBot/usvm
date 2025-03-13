package org.usvm.model

interface TsField : Base {
    val signature: TsFieldSignature
    val enclosingClass: TsClass?

    val name: String
        get() = signature.name

    val type: TsType
        get() = signature.type
}

class TsFieldImpl(
    override val signature: TsFieldSignature,
    override val modifiers: TsModifiers = TsModifiers.EMPTY,
    val isOptional: Boolean = false,  // '?'
    val isDefinitelyAssigned: Boolean = false, // '!'
) : TsField {
    override var enclosingClass: TsClass? = null

    override val decorators: List<TsDecorator>
        get() = error("Fields do not have decorators")

    override fun toString(): String {
        return signature.toString()
    }
}
