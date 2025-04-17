package org.usvm.dataflow.ts.infer

import mu.KotlinLogging
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnionType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.ANONYMOUS_CLASS_PREFIX
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.usvm.dataflow.ts.util.toStringLimited

private val logger = KotlinLogging.logger {}

sealed interface EtsTypeFact {

    fun toPrettyString(): String {
        return toString()
    }

    sealed interface BasicType : EtsTypeFact

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

    data class ArrayEtsTypeFact(
        val elementType: EtsTypeFact,
    ) : BasicType {
        override fun toString(): String = "Array<$elementType>"
    }

    @ConsistentCopyVisibility
    data class ObjectEtsTypeFact private constructor(
        val cls: EtsType?,
        val properties: Map<String, EtsTypeFact>,
    ) : BasicType {
        companion object {
            operator fun invoke(
                cls: EtsType?,
                properties: Map<String, EtsTypeFact>,
            ): ObjectEtsTypeFact {
                if (cls is EtsUnclearRefType && cls.typeName == "Object") {
                    return ObjectEtsTypeFact(null, properties)
                }
                return ObjectEtsTypeFact(cls, properties)
            }
        }

        override fun toString(): String {
            val clsName = cls?.typeName?.takeUnless { it.startsWith(ANONYMOUS_CLASS_PREFIX) } ?: "Object"
            val funProps = properties.entries
                .filter { it.value is FunctionEtsTypeFact }
                .filterNot { it.key == CONSTRUCTOR_NAME }
                .filterNot { it.key == INSTANCE_INIT_METHOD_NAME }
                .sortedBy { it.key }
            val nonFunProps = properties.entries
                .filter { it.value !is FunctionEtsTypeFact }
                .sortedBy { it.key }
            val props = (funProps + nonFunProps).joinToString(", ") { (name, type) -> "$name: $type" }
            return "$clsName { $props }"
        }

        // Object {
        // ..foo: Object {
        // ....bar: string
        // ..}
        // }
        override fun toPrettyString(): String {
            val clsName = cls?.typeName ?: "Object"
            val funProps = properties.entries
                .filter { it.value is FunctionEtsTypeFact }
                .sortedBy { it.key }
            val nonFunProps = properties.entries
                .filter { it.value !is FunctionEtsTypeFact }
                .sortedBy { it.key }
            return buildString {
                appendLine("$clsName {")
                for ((name, type) in funProps) {
                    appendLine("  $name: $type")
                }
                for ((name, type) in nonFunProps) {
                    appendLine("$name: ${type.toPrettyString()}".lines().joinToString("\n") { "  $it" })
                }
                append("}")
            }
        }

        override fun equals(other: Any?): Boolean {
            if (other !is ObjectEtsTypeFact) return false

            if (other.cls != null && other.cls == cls) return true

            return properties == other.properties
        }

        override fun hashCode(): Int {
            if (cls == null) return properties.hashCode()

            return cls.hashCode()
        }
    }

    data class UnionEtsTypeFact(
        val types: Set<EtsTypeFact>,
    ) : EtsTypeFact {
        init {
            require(types.isNotEmpty()) {
                "An empty set of types is passed as an union type"
            }
        }

        override fun toString(): String {
            return types.map {
                when (it) {
                    is UnionEtsTypeFact, is IntersectionEtsTypeFact -> "($it)"
                    else -> it.toString()
                }
            }.sorted().joinToString(" | ")
        }

        override fun toPrettyString(): String {
            return types.map {
                when (it) {
                    is UnionEtsTypeFact, is IntersectionEtsTypeFact -> "($it)"
                    else -> it.toString()
                }
            }.sorted().joinToString(" | ")
        }
    }

    data class IntersectionEtsTypeFact(
        val types: Set<EtsTypeFact>,
    ) : EtsTypeFact {
        init {
            require(types.isNotEmpty()) {
                "An empty set of types is passed as an intersection type"
            }
        }

        override fun toString(): String {
            return types.map {
                when (it) {
                    is UnionEtsTypeFact, is IntersectionEtsTypeFact -> "($it)"
                    else -> it.toString()
                }
            }.sorted().joinToString(" & ")
        }

        override fun toPrettyString(): String {
            return types.map {
                when (it) {
                    is UnionEtsTypeFact, is IntersectionEtsTypeFact -> "($it)"
                    else -> it.toString()
                }
            }.sorted().joinToString(" & ")
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

        fun from(type: EtsType): EtsTypeFact = when (type) {
            is EtsAnyType -> AnyEtsTypeFact
            is EtsUnknownType -> UnknownEtsTypeFact
            is EtsUnionType -> UnionEtsTypeFact(type.types.map { from(it) }.toSet())
            is EtsBooleanType -> BooleanEtsTypeFact
            is EtsNumberType -> NumberEtsTypeFact
            is EtsStringType -> StringEtsTypeFact
            is EtsNullType -> NullEtsTypeFact
            is EtsUndefinedType -> UndefinedEtsTypeFact
            is EtsClassType -> ObjectEtsTypeFact(type, emptyMap())
            is EtsFunctionType -> FunctionEtsTypeFact
            is EtsArrayType -> ArrayEtsTypeFact(elementType = from(type.elementType))
            is EtsUnclearRefType -> ObjectEtsTypeFact(type, emptyMap())
            else -> {
                // logger.warn { "Could not create type fact from ${type::class.simpleName}: $type" }
                UnknownEtsTypeFact
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

private fun findDuplicateGuard(
    fact: EtsTypeFact,
    guard: EtsTypeFact.BasicType,
): EtsTypeFact.GuardedTypeFact? {
    if (fact !is EtsTypeFact.GuardedTypeFact) return null
    if (fact.guard == guard) return fact
    return findDuplicateGuard(fact.type, guard)
}

fun EtsTypeFact.toType(): EtsType? = when (this) {
    is EtsTypeFact.ObjectEtsTypeFact -> cls

    is EtsTypeFact.ArrayEtsTypeFact -> EtsArrayType(
        elementType = elementType.toType() ?: EtsUnknownType,
        dimensions = 1,
    )

    EtsTypeFact.AnyEtsTypeFact -> EtsAnyType
    EtsTypeFact.BooleanEtsTypeFact -> EtsBooleanType
    EtsTypeFact.FunctionEtsTypeFact -> null // TODO: function type
    EtsTypeFact.NullEtsTypeFact -> EtsNullType
    EtsTypeFact.NumberEtsTypeFact -> EtsNumberType
    EtsTypeFact.StringEtsTypeFact -> EtsStringType
    EtsTypeFact.UndefinedEtsTypeFact -> EtsUndefinedType
    EtsTypeFact.UnknownEtsTypeFact -> EtsUnknownType

    is EtsTypeFact.UnionEtsTypeFact -> {
        val types = this.types.map { it.toType() }

        if (types.any { it == null }) {
            logger.warn { "Cannot convert union type fact to type: ${this.toStringLimited()}" }
            null
        } else {
            EtsUnionType(types.map { it!! })
        }
    }

    is EtsTypeFact.GuardedTypeFact -> null
    is EtsTypeFact.IntersectionEtsTypeFact -> null
}
