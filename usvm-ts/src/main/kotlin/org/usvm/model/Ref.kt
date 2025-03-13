package org.usvm.model

interface TsRef : TsValue

data class TsThis(
    override val type: TsClassType,
) : TsRef {
    override fun toString(): String = "this"

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsParameterRef(
    val index: Int,
    override val type: TsType,
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
    override val type: TsType,
) : TsRef, TsLValue {
    override fun toString(): String {
        return "$array[$index]"
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface TsFieldRef : TsRef, TsLValue {
    val field: TsFieldSignature

    override val type: TsType
        get() = this.field.type
}

data class TsInstanceFieldRef(
    val instance: TsLocal,
    override val field: TsFieldSignature,
) : TsFieldRef {
    override fun toString(): String {
        return "$instance.${field.name}"
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsStaticFieldRef(
    override val field: TsFieldSignature,
) : TsFieldRef {
    override fun toString(): String {
        return "${field.enclosingClass.name}.${field.name}"
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsInstancePropertyRef(
    val instance: TsLocal,
    val propertyName: String,
) : TsRef, TsLValue {
    override val type: TsType
        get() = TsUnknownType

    override fun toString(): String {
        return "${instance}.${propertyName}"
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        error("Not implemented")
    }
}

data class TsStaticPropertyRef(
    val declaringClass: TypeName,
    val propertyName: String,
) : TsRef, TsLValue {
    override val type: TsType
        get() = TsUnknownType

    override fun toString(): String {
        return "${declaringClass}.${propertyName}"
    }

    override fun <R> accept(visitor: TsValue.Visitor<R>): R {
        error("Not implemented")
    }
}
