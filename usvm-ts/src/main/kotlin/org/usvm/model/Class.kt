package org.usvm.model

import org.jacodb.ets.base.CONSTRUCTOR_NAME
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsBaseModel
import org.jacodb.ets.model.EtsDecorator
import org.jacodb.ets.model.EtsModifiers

interface TsClass : EtsBaseModel {
    val signature: TsClassSignature
    val typeParameters: List<EtsType>
    val fields: List<TsField>
    val methods: List<TsMethod>
    val ctor: TsMethod
    val superClass: TsClassSignature?
    val implementedInterfaces: List<TsClassSignature>
}

class TsClassImpl(
    override val signature: TsClassSignature,
    override val fields: List<TsField>,
    override val methods: List<TsMethod>,
    override val superClass: TsClassSignature? = null,
    override val implementedInterfaces: List<TsClassSignature> = emptyList(),
    override val typeParameters: List<EtsType> = emptyList(),
    override val modifiers: EtsModifiers = EtsModifiers.EMPTY,
    override val decorators: List<EtsDecorator> = emptyList(),
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
        methods.first { method -> method.name == CONSTRUCTOR_NAME }

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
