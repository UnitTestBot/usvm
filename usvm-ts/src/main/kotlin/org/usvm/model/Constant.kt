package org.usvm.model

interface TsConstant : TsImmediate

data class TsStringConstant(
    val value: String,
) : TsConstant {
    override fun toString(): String {
        return "\"$value\""
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsBooleanConstant(
    val value: Boolean,
) : TsConstant {
    override fun toString(): String {
        return if (value) "true" else "false"
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }

    companion object {
        val TRUE = TsBooleanConstant(true)
        val FALSE = TsBooleanConstant(false)
    }
}

data class TsNumberConstant(
    val value: Double,
) : TsConstant {
    override fun toString(): String {
        return value.toString()
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object TsNullConstant : TsConstant {
    override fun toString(): String = "null"

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object TsUndefinedConstant : TsConstant {
    override fun toString(): String = "undefined"

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}
