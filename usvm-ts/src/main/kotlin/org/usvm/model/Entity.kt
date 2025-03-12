package org.usvm.model

interface TsEntity {
    val type: TsType

    // override val typeName: String
    //     get() = type.typeName

    interface Visitor<out R> :
        TsValue.Visitor<R>,
        TsExpr.Visitor<R> {

        fun visit(value: TsRawEntity): R {
            if (this is Default) {
                return defaultVisit(value)
            }
            error("Cannot handle ${value::class.java.simpleName}: $value")
        }

        interface Default<out R> : Visitor<R>,
            TsValue.Visitor.Default<R>,
            TsExpr.Visitor.Default<R> {

            override fun defaultVisit(value: TsValue): R = defaultVisit(value as TsEntity)
            override fun defaultVisit(expr: TsExpr): R = defaultVisit(expr as TsEntity)

            fun defaultVisit(value: TsEntity): R
        }
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class TsRawEntity(
    val kind: String,
    val extra: Map<String, Any> = emptyMap(),
    override val type: TsType,
) : TsEntity {
    override fun toString(): String {
        return "$kind $extra: $type"
    }

    override fun <R> accept(visitor: TsEntity.Visitor<R>): R {
        return visitor.visit(this)
    }
}
