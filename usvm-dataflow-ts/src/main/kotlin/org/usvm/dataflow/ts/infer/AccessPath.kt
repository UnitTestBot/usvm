package org.usvm.dataflow.ts.infer

import org.jacodb.ets.base.EtsArrayAccess
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsConstant
import org.jacodb.ets.base.EtsEntity
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsStaticFieldRef
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsValue
import org.jacodb.ets.model.EtsClassSignature

data class AccessPath(val base: AccessPathBase, val accesses: List<Accessor>) {
    operator fun plus(accessor: Accessor) = AccessPath(base, accesses + accessor)
    operator fun plus(accessors: List<Accessor>) = AccessPath(base, accesses + accessors)

    override fun toString(): String {
        return base.toString() + accesses.joinToString("") { it.toSuffix() }
    }
}

fun List<Accessor>.startsWith(other: List<Accessor>): Boolean {
    return this.take(other.size) == other
}

fun AccessPath?.startsWith(other: AccessPath?): Boolean {
    if (this == null || other == null) {
        return false
    }
    if (this.base != other.base) {
        return false
    }
    return this.accesses.startsWith(other.accesses)
}

fun List<Accessor>.hasDuplicateFields(limit: Int = 2): Boolean {
    // val counts = this.groupingBy { it }.eachCount()
    // return counts.any { it.value >= limit }
    val counts = mutableMapOf<Accessor, Int>()
    for (accessor in this) {
        val count = counts.getOrDefault(accessor, 0)
        counts[accessor] = count + 1
        if (count + 1 >= limit) {
            return true
        }
    }
    return false
}

sealed interface AccessPathBase {
    object This : AccessPathBase {
        override fun toString(): String = "<this>"
    }

    data class Static(val clazz: EtsClassSignature) : AccessPathBase {
        override fun toString(): String = "static(${clazz.name})"
    }

    data class Arg(val index: Int) : AccessPathBase {
        override fun toString(): String = "arg($index)"
    }

    data class Local(val name: String) : AccessPathBase {
        override fun toString(): String = "local($name)"

        fun tryGetOrdering(): Int? {
            if (name.startsWith("%")) {
                val ix = name.substring(1).toIntOrNull()
                if (ix != null) {
                    return ix
                }
            }
            if (name.startsWith("\$v")) {
                val ix = name.substring(2).toIntOrNull()
                if (ix != null) {
                    return 10_000 + ix
                }
            }
            if (name.startsWith("\$temp")) {
                val ix = name.substring(5).toIntOrNull()
                if (ix != null) {
                    return 20_000 + ix
                }
            }
            if (name.startsWith("_tmp")) {
                val ix = name.substring(4).toIntOrNull()
                if (ix != null) {
                    return 30_000 + ix
                }
            }
            return null
        }
    }

    data class Const(val constant: EtsConstant) : AccessPathBase {
        override fun toString(): String = "const($constant)"
    }
}

fun EtsValue.toBase(): AccessPathBase = when (this) {
    is EtsConstant -> AccessPathBase.Const(this)
    is EtsLocal -> if (name == "this") AccessPathBase.This else AccessPathBase.Local(name)
    is EtsThis -> AccessPathBase.This
    is EtsParameterRef -> AccessPathBase.Arg(index)
    else -> error("Unable to build access path base for ${this::class.java.simpleName}: $this")
}

fun EtsEntity.toPathOrNull(): AccessPath? = when (this) {
    is EtsConstant -> AccessPath(toBase(), emptyList())

    is EtsLocal -> AccessPath(toBase(), emptyList())

    is EtsThis -> AccessPath(toBase(), emptyList())

    is EtsParameterRef -> AccessPath(toBase(), emptyList())

    is EtsArrayAccess -> array.toPathOrNull()?.let {
        it + ElementAccessor
    }

    is EtsInstanceFieldRef -> instance.toPathOrNull()?.let {
        it + FieldAccessor(field.name)
    }

    is EtsStaticFieldRef -> {
        val base = AccessPathBase.Static(field.enclosingClass)
        AccessPath(base, listOf(FieldAccessor(field.name)))
    }

    is EtsCastExpr -> arg.toPathOrNull()

    else -> null
}

fun EtsEntity.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for ${this::class.java.simpleName}: $this")
}
