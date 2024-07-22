package org.usvm.dataflow.ts.infer

import org.jacodb.ets.base.EtsType

sealed interface EtsTypeFact {
    sealed interface BasicType : EtsTypeFact

    fun union(other: EtsTypeFact): EtsTypeFact {
        if (this == other) return this

        return when {
            this is ObjectEtsTypeFact && other is ObjectEtsTypeFact -> union(this, other)
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
            is StringEtsTypeFact -> when (other) {
                is UnionEtsTypeFact -> intersect(other, this)
                is IntersectionEtsTypeFact -> intersect(other, this)
                is GuardedTypeFact -> intersect(other, this)
                else -> null
            }

            is NumberEtsTypeFact -> when (other) {
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
            return types.joinToString(" | ")
        }
    }

    data class IntersectionEtsTypeFact(
        val types: Set<EtsTypeFact>,
    ) : EtsTypeFact {
        override fun toString(): String {
            return types.joinToString(" & ")
        }
    }

    data class GuardedTypeFact(
        val guard: BasicType,
        val guardNegated: Boolean,
        val type: EtsTypeFact,
    ) : EtsTypeFact

    companion object {
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

            val commonCls = obj1.cls.takeIf { it == obj2.cls }
            val commonObject = ObjectEtsTypeFact(commonCls, commonProperties)

            val o1 = ObjectEtsTypeFact(obj1.cls, o1OnlyProperties)
            val o2 = ObjectEtsTypeFact(obj2.cls, o2OnlyProperties)

            if (commonProperties.isEmpty()) {
                return mkUnionType(o1, o2)
            }

            if (o1OnlyProperties.isEmpty() && o2OnlyProperties.isEmpty()) {
                return commonObject
            }

            return mkIntersectionType(commonObject, mkUnionType(o1, o2))
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
