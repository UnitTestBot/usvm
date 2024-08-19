package org.usvm.dataflow.ts.infer

import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsArrayObjectType
import org.jacodb.ets.base.EtsArrayType
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsFunctionType
import org.jacodb.ets.base.EtsLiteralType
import org.jacodb.ets.base.EtsNeverType
import org.jacodb.ets.base.EtsNullType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsTupleType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnclearRefType
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsUnionType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.base.EtsVoidType

sealed interface EtsTypeFact {

    sealed interface BasicType : EtsTypeFact

    fun union(other: EtsTypeFact): EtsTypeFact {
        if (this == other) return this

        return when {
            this is ObjectEtsTypeFact && other is ObjectEtsTypeFact -> union(this, other)
            this is ObjectEtsTypeFact && other is StringEtsTypeFact -> union(this, other)
            this is UnionEtsTypeFact -> union(this, other)
            this is IntersectionEtsTypeFact -> union(this, other)
            this is GuardedTypeFact -> union(this, other)
            other is UnionEtsTypeFact -> union(other, this)
            other is IntersectionEtsTypeFact -> union(other, this)
            other is GuardedTypeFact -> union(other, this)
            else -> mkUnionType(this, other)
        }
    }

    fun intersect(other: EtsTypeFact): EtsTypeFact? {
        if (this == other) return this

        if (other is UnknownEtsTypeFact) return this
        if (other is AnyEtsTypeFact) return other

        return when (this) {
            is UnknownEtsTypeFact -> other

            is AnyEtsTypeFact -> this

            is StringEtsTypeFact,
            is NumberEtsTypeFact,
            is BooleanEtsTypeFact,
            is NullEtsTypeFact,
            is UndefinedEtsTypeFact,
                -> when (other) {
                is UnionEtsTypeFact -> intersect(other, this)
                is IntersectionEtsTypeFact -> intersect(other, this)
                is GuardedTypeFact -> intersect(other, this)
                else -> null
            }

            is FunctionEtsTypeFact -> when (other) {
                is ObjectEtsTypeFact -> mkIntersectionType(this, other)
                is UnionEtsTypeFact -> intersect(other, this)
                is IntersectionEtsTypeFact -> intersect(other, this)
                is GuardedTypeFact -> intersect(other, this)
                else -> null
            }

            is ObjectEtsTypeFact -> when (other) {
                is ObjectEtsTypeFact -> intersect(this, other)
                is StringEtsTypeFact -> intersect(this, other)
                is FunctionEtsTypeFact -> mkIntersectionType(this, other)
                is UnionEtsTypeFact -> intersect(other, this)
                is IntersectionEtsTypeFact -> intersect(other, this)
                is GuardedTypeFact -> intersect(other, this)
                else -> null
            }

            is UnionEtsTypeFact -> intersect(this, other)
            is IntersectionEtsTypeFact -> intersect(this, other)
            is GuardedTypeFact -> intersect(this, other)
        }
    }

    object UnknownEtsTypeFact : EtsTypeFact, BasicType {
        override fun toString(): String = "unknown"
    }

    object AnyEtsTypeFact : BasicType {
        override fun toString(): String = "any"
    }

    object StringEtsTypeFact : BasicType {
        override fun toString(): String = "string"
    }

    object NumberEtsTypeFact : BasicType {
        override fun toString(): String = "number"
    }

    object BooleanEtsTypeFact : BasicType {
        override fun toString(): String = "boolean"
    }

    object NullEtsTypeFact : BasicType {
        override fun toString(): String = "null"
    }

    object UndefinedEtsTypeFact : BasicType {
        override fun toString(): String = "undefined"
    }

    // object VoidEtsTypeFact : BasicType {
    //     override fun toString(): String = "void"
    // }
    //
    // object NeverEtsTypeFact : BasicType {
    //     override fun toString(): String = "never"
    // }

    object FunctionEtsTypeFact : BasicType {
        override fun toString(): String = "function"
    }

    data class ObjectEtsTypeFact(
        val cls: EtsType?,
        val properties: Map<String, EtsTypeFact>,
    ) : BasicType {
        override fun toString(): String {
            val clsName = cls?.typeName ?: "Object"
            val props = properties.entries.joinToString(", ") { (name, type) -> "$name: $type" }
            return "$clsName { $props }"
        }
    }

    data class UnionEtsTypeFact(
        val types: Set<EtsTypeFact>,
    ) : EtsTypeFact {
        override fun toString(): String {
            return types.joinToString(" | ") {
                when (it) {
                    is UnionEtsTypeFact, is IntersectionEtsTypeFact -> "(${it})"
                    else -> it.toString()
                }
            }
        }
    }

    data class IntersectionEtsTypeFact(
        val types: Set<EtsTypeFact>,
    ) : EtsTypeFact {
        override fun toString(): String {
            return types.joinToString(" & ") {
                when (it) {
                    is UnionEtsTypeFact, is IntersectionEtsTypeFact -> "(${it})"
                    else -> it.toString()
                }
            }
        }
    }

    data class GuardedTypeFact(
        val guard: BasicType,
        val guardNegated: Boolean,
        val type: EtsTypeFact,
    ) : EtsTypeFact

    companion object {
        internal val allStringProperties = listOf(
            "length",
            "constructor",
            "anchor",
            "at",
            "big",
            "blink",
            "bold",
            "charAt",
            "charCodeAt",
            "codePointAt",
            "concat",
            "endsWith",
            "fontcolor",
            "fontsize",
            "fixed",
            "includes",
            "indexOf",
            "isWellFormed",
            "italics",
            "lastIndexOf",
            "link",
            "localeCompare",
            "match",
            "matchAll",
            "normalize",
            "padEnd",
            "padStart",
            "repeat",
            "replace",
            "replaceAll",
            "search",
            "slice",
            "small",
            "split",
            "strike",
            "sub",
            "substr",
            "substring",
            "sup",
            "startsWith",
            "toString",
            "toWellFormed",
            "trim",
            "trimStart",
            "trimLeft",
            "trimEnd",
            "trimRight",
            "toLocaleLowerCase",
            "toLocaleUpperCase",
            "toLowerCase",
            "toUpperCase",
            "valueOf",
        )

        private fun intersect(unionType: UnionEtsTypeFact, other: EtsTypeFact): EtsTypeFact {
            // todo: push intersection
            return mkIntersectionType(unionType, other)
        }

        private fun intersect(intersectionType: IntersectionEtsTypeFact, other: EtsTypeFact): EtsTypeFact? {
            val result = hashSetOf<EtsTypeFact>()
            for (type in intersectionType.types) {
                val intersection = type.intersect(other) ?: return null
                if (intersection is IntersectionEtsTypeFact) {
                    result.addAll(intersection.types)
                } else {
                    result.add(intersection)
                }
            }
            return mkIntersectionType(result)
        }

        private fun intersect(guardedType: GuardedTypeFact, other: EtsTypeFact): EtsTypeFact? {
            if (other is GuardedTypeFact) {
                if (other.guard == guardedType.guard) {
                    return if (other.guardNegated == guardedType.guardNegated) {
                        guardedType.type.intersect(other.type)?.withGuard(guardedType.guard, guardedType.guardNegated)
                    } else {
                        guardedType.type.union(other.type)
                    }
                }
            }

            // todo: evaluate types
            return mkIntersectionType(guardedType, other)
        }

        private fun intersect(obj1: ObjectEtsTypeFact, obj2: ObjectEtsTypeFact): EtsTypeFact? {
            val intersectionProperties = obj1.properties.toMutableMap()
            for ((property, type) in obj2.properties) {
                val currentType = intersectionProperties[property]
                if (currentType == null) {
                    intersectionProperties[property] = type
                    continue
                }

                intersectionProperties[property] = currentType.intersect(type) ?: return null
            }

            val intersectionCls = obj1.cls.takeIf { it == obj2.cls }
            return ObjectEtsTypeFact(intersectionCls, intersectionProperties)
        }

        private fun intersect(obj: ObjectEtsTypeFact, string: StringEtsTypeFact): EtsTypeFact? {
            if (obj.cls == EtsStringType) return string
            if (obj.cls != null) return null

            val intersectionProperties = obj.properties
                .filter { it.key in allStringProperties }
                .mapValues { (_, type) ->
                    // TODO: intersect with the corresponding type of String's property
                    type
                }

            return ObjectEtsTypeFact(obj.cls, intersectionProperties)
        }

        private fun union(unionType: UnionEtsTypeFact, other: EtsTypeFact): EtsTypeFact {
            val result = hashSetOf<EtsTypeFact>()
            for (type in unionType.types) {
                val union = type.union(other)
                if (union is UnionEtsTypeFact) {
                    result.addAll(union.types)
                } else {
                    result.add(union)
                }
            }
            return mkUnionType(result)
        }

        private fun union(guardedType: GuardedTypeFact, other: EtsTypeFact): EtsTypeFact {
            // todo: evaluate types
            return mkUnionType(guardedType, other)
        }

        private fun union(intersectionType: IntersectionEtsTypeFact, other: EtsTypeFact): EtsTypeFact {
            // todo: push union
            return mkUnionType(intersectionType, other)
        }

        private fun union(obj1: ObjectEtsTypeFact, obj2: ObjectEtsTypeFact): EtsTypeFact {
            if (obj1.cls != null && obj2.cls != null && obj1.cls != obj2.cls) {
                return mkUnionType(obj1, obj2)
            }

            val commonProperties = obj1.properties.keys.intersect(obj2.properties.keys).associateWith { property ->
                val thisType = obj1.properties.getValue(property)
                val otherType = obj2.properties.getValue(property)
                thisType.union(otherType)
            }

            val o1OnlyProperties = obj1.properties.filter { it.key !in obj2.properties }
            val o2OnlyProperties = obj2.properties.filter { it.key !in obj1.properties }

            val o1 = ObjectEtsTypeFact(obj1.cls, o1OnlyProperties)
            val o2 = ObjectEtsTypeFact(obj2.cls, o2OnlyProperties)

            if (commonProperties.isEmpty()) {
                return mkUnionType(o1, o2)
            }

            val commonCls = obj1.cls.takeIf { it == obj2.cls }
            val commonObject = ObjectEtsTypeFact(commonCls, commonProperties)

            if (o1OnlyProperties.isEmpty() && o2OnlyProperties.isEmpty()) {
                return commonObject
            }

            return mkIntersectionType(commonObject, mkUnionType(o1, o2))
        }

        private fun union(obj: ObjectEtsTypeFact, string: StringEtsTypeFact): EtsTypeFact {
            if (obj.cls == EtsStringType) return string
            if (obj.cls != null) return mkUnionType(obj, string)

            for (p in obj.properties.keys) {
                if (p !in allStringProperties) {
                    return mkUnionType(obj, string)
                }
            }

            return string
        }

        fun mkUnionType(vararg types: EtsTypeFact): EtsTypeFact = mkUnionType(types.toHashSet())

        fun mkUnionType(types: Set<EtsTypeFact>): EtsTypeFact {
            if (types.size == 1) return types.single()
            return UnionEtsTypeFact(types)
        }

        fun mkIntersectionType(vararg types: EtsTypeFact): EtsTypeFact = mkIntersectionType(types.toHashSet())

        fun mkIntersectionType(types: Set<EtsTypeFact>): EtsTypeFact {
            if (types.size == 1) return types.single()
            return IntersectionEtsTypeFact(types)
        }

        fun from(type: EtsType): EtsTypeFact {
            return when (type) {
                is EtsAnyType -> AnyEtsTypeFact
                is EtsUnknownType -> UnknownEtsTypeFact
                is EtsUnionType -> UnionEtsTypeFact(type.types.map { from(it) }.toSet())
                is EtsTupleType -> TODO()
                is EtsBooleanType -> BooleanEtsTypeFact
                is EtsNumberType -> NumberEtsTypeFact
                is EtsStringType -> StringEtsTypeFact
                is EtsNullType -> NullEtsTypeFact
                is EtsUndefinedType -> UndefinedEtsTypeFact
                is EtsVoidType -> TODO()
                is EtsNeverType -> TODO()
                is EtsLiteralType -> TODO()
                is EtsClassType -> ObjectEtsTypeFact(type, emptyMap())
                is EtsFunctionType -> FunctionEtsTypeFact
                is EtsArrayType -> ObjectEtsTypeFact(
                    cls = type,
                    properties = mapOf(
                        "index" to ObjectEtsTypeFact(
                            cls = null,
                            properties = mapOf(
                                "value" to from(type.elementType),
                                "name" to StringEtsTypeFact
                            )
                        ),
                        "length" to NumberEtsTypeFact
                    )
                )

                is EtsArrayObjectType -> TODO()
                is EtsUnclearRefType -> ObjectEtsTypeFact(type, emptyMap())
                else -> TODO()
            }
        }
    }
}

fun EtsTypeFact.withGuard(guard: EtsTypeFact.BasicType, guardNegated: Boolean): EtsTypeFact {
    val duplicateGuard = findDuplicateGuard(this, guard)

    if (duplicateGuard != null) {
        if (guardNegated == duplicateGuard.guardNegated) return this

        TODO("Same guard with different sign")
    }

    return EtsTypeFact.GuardedTypeFact(guard, guardNegated, this)
}

private fun findDuplicateGuard(fact: EtsTypeFact, guard: EtsTypeFact.BasicType): EtsTypeFact.GuardedTypeFact? {
    if (fact !is EtsTypeFact.GuardedTypeFact) return null
    if (fact.guard == guard) return fact
    return findDuplicateGuard(fact.type, guard)
}
