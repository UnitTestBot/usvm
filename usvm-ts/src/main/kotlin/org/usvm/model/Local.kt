package org.usvm.model

data class TsLocal(
    val name: String,
    override val type: TsType,
) : TsImmediate, TsLValue {
    override fun toString(): String {
        return name
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}
