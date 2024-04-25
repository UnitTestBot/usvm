package org.usvm.jacodb

import org.jacodb.api.core.CoreType

interface GoType : CoreType

class NullType: GoType {
    override val typeName = "null"
}

class LongType: GoType {
    override val typeName = "long"
}

class ArrayType(
    val len: Long,
    val elementType: GoType
) : GoType {
    override val typeName: String
        get() = "[${len}]${elementType.typeName}"
}

class BasicType(
    override val typeName: String
) : GoType

class ChanType(
    val direction: Long,
    val elementType: GoType
) : GoType {
    override val typeName: String
        get(): String {
            var res = elementType.typeName
            if (direction == 0L) {
                res = "chan $res"
            }
            else if (direction == 1L) {
                res = "<-chan $res"
            }
            else if (direction == 2L) {
                res = "chan <-$res"
            }
            return res
        }
}

class InterfaceType() : GoType {
    override val typeName = "Any"
}

class MapType(
    val keyType: GoType,
    val valueType: GoType
) : GoType {
    override val typeName: String
        get() = "map[${keyType.typeName}]${valueType.typeName}"
}

class NamedType(
    var underlyingType: GoType
) : GoType {
    override val typeName: String
        get() = underlyingType.typeName
}

class PointerType(
    var baseType: GoType
) : GoType {
    override val typeName: String
        get() = baseType.typeName
}

class SignatureType(
    val params: TupleType,
    val results: TupleType
) : GoType {
    override val typeName: String
        get(): String {
            return "func " + params.typeName + " " + results.typeName
        }
}

class SliceType(
    val elementType: GoType
) : GoType {
    override val typeName: String
        get() = "[]${elementType.typeName}"
}

class StructType(
    val fields: List<GoType>?,
    val tags: List<String>?
) : GoType {
    override val typeName: String
        get(): String {
            var res = "struct {\n"
            fields!!.forEachIndexed { ind, elem ->
                res += elem.typeName
                if (tags != null && tags.size > ind) {
                    res += " " + tags[ind]
                }
                res += "\n"
            }
            return "$res}"
        }
}

class TupleType(
    val names: List<String>
) : GoType {
    override val typeName: String
        get(): String {
            return "(" + names.joinToString(", ") + ")"
        }
}

class TypeParam(
    override val typeName: String
) : GoType

class UnionType(
    val terms: List<GoType>
) : GoType {
    override val typeName: String
        get(): String {
            var res = "enum {\n"
            for (t in terms) {
                res += t.typeName + ",\n"
            }
            return "$res}"
        }
}

class OpaqueType(
    override val typeName: String
) : GoType
