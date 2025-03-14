package org.usvm.model

interface TsType : TypeName {
    interface Visitor<out R> {
        fun visit(type: TsAnyType): R
        fun visit(type: TsUnknownType): R
        fun visit(type: TsUnclearType): R
        fun visit(type: TsGenericType): R

        fun visit(type: TsUnionType): R
        fun visit(type: TsIntersectionType): R

        // Primitive
        fun visit(type: TsBooleanType): R
        fun visit(type: TsNumberType): R
        fun visit(type: TsStringType): R
        fun visit(type: TsNullType): R
        fun visit(type: TsUndefinedType): R
        fun visit(type: TsVoidType): R
        fun visit(type: TsNeverType): R
        fun visit(type: TsLiteralType): R

        // Ref
        fun visit(type: TsClassType): R
        fun visit(type: TsArrayType): R
        fun visit(type: TsTupleType): R
        fun visit(type: TsFunctionType): R

        fun visit(type: TsRawType): R {
            if (this is Default) {
                return defaultVisit(type)
            }
            error("Cannot handle ${type::class.java.simpleName}: $type")
        }

        interface Default<R> : Visitor<R> {
            override fun visit(type: TsAnyType): R = defaultVisit(type)
            override fun visit(type: TsUnknownType): R = defaultVisit(type)
            override fun visit(type: TsUnclearType): R = defaultVisit(type)
            override fun visit(type: TsGenericType): R = defaultVisit(type)

            override fun visit(type: TsUnionType): R = defaultVisit(type)
            override fun visit(type: TsIntersectionType): R = defaultVisit(type)

            override fun visit(type: TsBooleanType): R = defaultVisit(type)
            override fun visit(type: TsNumberType): R = defaultVisit(type)
            override fun visit(type: TsStringType): R = defaultVisit(type)
            override fun visit(type: TsNullType): R = defaultVisit(type)
            override fun visit(type: TsUndefinedType): R = defaultVisit(type)
            override fun visit(type: TsVoidType): R = defaultVisit(type)
            override fun visit(type: TsNeverType): R = defaultVisit(type)
            override fun visit(type: TsLiteralType): R = defaultVisit(type)

            override fun visit(type: TsClassType): R = defaultVisit(type)
            override fun visit(type: TsArrayType): R = defaultVisit(type)
            override fun visit(type: TsTupleType): R = defaultVisit(type)
            override fun visit(type: TsFunctionType): R = defaultVisit(type)

            fun defaultVisit(type: TsType): R
        }
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class TsRawType(
    val kind: String,
    val extra: Map<String, Any> = emptyMap(),
) : TsType {
    override val typeName: String
        get() = kind

    override fun toString(): String {
        return "$kind $extra"
    }

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object TsAnyType : TsType {
    override val typeName: String
        get() = "any"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object TsUnknownType : TsType {
    override val typeName: String
        get() = "unknown"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsUnclearType(
    override val typeName: String,
    val typeParameters: List<TsType>,
) : TsType {
    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsGenericType(
    override val typeName: String,
    val constraint: TsType? = null,
    val default: TsType? = null,
) : TsType {
    override fun toString(): String {
        return typeName + (constraint?.let { " extends $it" } ?: "") + (default?.let { " = $it" } ?: "")
    }

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsUnionType(
    val types: List<TsType>,
) : TsType {
    override val typeName: String
        get() = types.joinToString(separator = " | ") {
            if (it is TsUnionType || it is TsIntersectionType) {
                "(${it.typeName})"
            } else {
                it.typeName
            }
        }

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsIntersectionType(
    val types: List<TsType>,
) : TsType {
    override val typeName: String
        get() = types.joinToString(separator = " & ") {
            if (it is TsUnionType || it is TsIntersectionType) {
                "(${it.typeName})"
            } else {
                it.typeName
            }
        }

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface TsPrimitiveType : TsType

object TsBooleanType : TsPrimitiveType {
    override val typeName: String
        get() = "boolean"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object TsNumberType : TsPrimitiveType {
    override val typeName: String
        get() = "number"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object TsStringType : TsPrimitiveType {
    override val typeName: String
        get() = "string"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object TsNullType : TsPrimitiveType {
    override val typeName: String
        get() = "null"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object TsUndefinedType : TsPrimitiveType {
    override val typeName: String
        get() = "undefined"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object TsVoidType : TsPrimitiveType {
    override val typeName: String
        get() = "void"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object TsNeverType : TsPrimitiveType {
    override val typeName: String
        get() = "never"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsLiteralType(
    val literalTypeName: String,
) : TsPrimitiveType {
    override val typeName: String
        get() = literalTypeName

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface TsRefType : TsType

data class TsClassType(
    val signature: TsClassSignature,
    val typeParameters: List<TsType> = emptyList(),
) : TsRefType {
    override val typeName: String
        get() = if (typeParameters.isNotEmpty()) {
            val generics = typeParameters.joinToString { it.typeName }
            "${signature.name}<$generics>"
        } else {
            signature.name
        }

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsArrayType(
    val elementType: TsType,
    val dimensions: Int,
) : TsRefType {
    override val typeName: String
        get() = elementType.typeName + "[]".repeat(dimensions)

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsTupleType(
    val types: List<TsType>,
) : TsRefType {
    override val typeName: String
        get() = types.joinToString(prefix = "[", postfix = "]") { it.typeName }

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TsFunctionType(
    val signature: TsMethodSignature,
    val typeParameters: List<TsType> = emptyList(),
) : TsRefType {
    override val typeName: String
        get() = if (typeParameters.isNotEmpty()) {
            val generics = typeParameters.joinToString { it.typeName }
            "${signature.name}<$generics>"
        } else {
            signature.name
        }

    override fun toString(): String = typeName

    override fun <R> accept(visitor: TsType.Visitor<R>): R {
        return visitor.visit(this)
    }
}
