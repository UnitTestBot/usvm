package org.usvm.model

import org.jacodb.ets.base.CONSTRUCTOR_NAME
import org.usvm.util.createConstructor

interface TsClass : Base {
    val signature: TsClassSignature
    val typeParameters: List<TsType>
    val fields: List<TsField>
    val methods: List<TsMethod>
    val ctor: TsMethod
    val superClass: TsClassSignature?
    val implementedInterfaces: List<TsClassSignature>

    val declaringFile: TsFile?
    val declaringNamespace: TsNamespace?

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
        fields.forEach { (it as TsFieldImpl).declaringClass = this }
        methods.forEach { (it as TsMethodImpl).enclosingClass = this }
    }

    override var declaringFile: TsFile? = null
    override var declaringNamespace: TsNamespace? = null

    override val ctor: TsMethod =
        methods.firstOrNull { method -> method.name == CONSTRUCTOR_NAME }
            ?: createConstructor(signature)

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
