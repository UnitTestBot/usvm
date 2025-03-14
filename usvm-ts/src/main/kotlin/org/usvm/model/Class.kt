package org.usvm.model

import org.jacodb.ets.base.CONSTRUCTOR_NAME

interface TsClass : Base {
    val signature: TsClassSignature
    val typeParameters: List<TsType>
    val fields: List<TsField>
    val methods: List<TsMethod>
    val ctor: TsMethod
    val superClass: TsClassSignature?
    val implementedInterfaces: List<TsClassSignature>

    val name: String
        get() = signature.name
}

class TsClassImpl(
    override val signature: TsClassSignature,
    override val fields: List<TsField>,
    override val methods: List<TsMethod>,
    override val superClass: TsClassSignature? = null,
    override val implementedInterfaces: List<TsClassSignature> = emptyList(),
    override val typeParameters: List<TsType> = emptyList(),
    override val modifiers: TsModifiers = TsModifiers.EMPTY,
    override val decorators: List<TsDecorator> = emptyList(),
) : TsClass {

    init {
        fields.forEach { field ->
            (field as TsFieldImpl).enclosingClass = this
        }
        methods.forEach { method ->
            (method as TsMethodImpl).enclosingClass = this
        }
    }

    override val ctor: TsMethod =
        methods.firstOrNull { method -> method.name == CONSTRUCTOR_NAME }
            ?: TsMethodImpl(
                signature = TsMethodSignature(
                    enclosingClass = signature,
                    name = CONSTRUCTOR_NAME,
                    parameters = emptyList(),
                    returnType = TsUndefinedType,
                ),
            )

    override fun toString(): String {
        return signature.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TsClassImpl

        return signature == other.signature
    }

    override fun hashCode(): Int {
        return signature.hashCode()
    }
}
