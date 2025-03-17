@file:Suppress("PropertyName")

package org.usvm.model

import org.jacodb.ets.model.EtsMethod

interface TsMethod : Base {
    val signature: TsMethodSignature
    val typeParameters: List<TsType>
    val cfg: TsBlockCfg

    val enclosingClass: TsClass?

    fun getLocalType(local: TsLocal): TsType

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
    val localType: Map<TsLocal, TsType> = emptyMap(),
    val etsMethod: EtsMethod? = null,
) : TsMethod {
    var _cfg: TsBlockCfg? = null

    override val cfg: TsBlockCfg
        get() = _cfg ?: TsBlockCfg.EMPTY

    override var enclosingClass: TsClass? = null

    override fun getLocalType(local: TsLocal): TsType {
        if (local.name == "this") {
            return TsClassType(signature = signature.enclosingClass)
        }
        return localType[local] ?: TsUnknownType
    }

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
