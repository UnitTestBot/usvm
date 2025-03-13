package org.usvm.model

interface TsRef : TsValue

data object TsThis : TsRef, TsImmediate {
    override fun toString(): String = "this"

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsParameterRef(
    val index: Int,
) : TsRef {
    override fun toString(): String {
        return "arg$index"
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsArrayAccess(
    val array: TsLocal,
    val index: TsValue,
) : TsRef, TsLValue {
    override fun toString(): String {
        return "$array[$index]"
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface TsFieldRef : TsRef, TsLValue {
    val fieldName: String
}

data class TsInstanceFieldRef(
    val instance: TsLocal,
    override val fieldName: String,
) : TsFieldRef {
    override fun toString(): String {
        return "${instance}.${fieldName}"
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsStaticFieldRef(
    val enclosingClass: TypeName,
    override val fieldName: String,
) : TsFieldRef {
    override fun toString(): String {
        return "${enclosingClass}.${fieldName}"
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}
