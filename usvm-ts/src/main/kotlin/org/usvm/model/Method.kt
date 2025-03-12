@file:Suppress("PropertyName")

package org.usvm.model

import org.jacodb.ets.graph.EtsCfg

interface TsMethod : Base {
    val signature: TsMethodSignature
    val typeParameters: List<TsType>
    val cfg: EtsCfg

    val enclosingClass: TsClass?

    val name: String
        get() = signature.name

    val parameters: List<TsMethodParameter>
        get() = signature.parameters

    val returnType: TsType
        get() = signature.returnType
}

class TsMethodImpl(
    override val signature: TsMethodSignature,
    override val typeParameters: List<TsType> = emptyList(),
    override val modifiers: TsModifiers = TsModifiers.EMPTY,
    override val decorators: List<TsDecorator> = emptyList(),
) : TsMethod {
    var _cfg: EtsCfg? = null

    override val cfg: EtsCfg
        get() = _cfg ?: EtsCfg.EMPTY

    override var enclosingClass: TsClass? = null

    override fun toString(): String {
        return signature.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TsMethodImpl

        return signature == other.signature
    }

    override fun hashCode(): Int {
        return signature.hashCode()
    }
}
