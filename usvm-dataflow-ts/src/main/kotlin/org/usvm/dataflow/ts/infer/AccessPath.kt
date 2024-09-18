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


sealed interface AccessPathBase {
    object This : AccessPathBase {
        override fun toString(): String = "<this>"
    }

    object Static : AccessPathBase {
        override fun toString(): String = "<static>"
    }

    data class Arg(val index: Int) : AccessPathBase {
        override fun toString(): String = "arg($index)"
    }

    data class Local(val name: String) : AccessPathBase {
        override fun toString(): String = "local($name)"
    }

    data class Const(val constant: EtsConstant) : AccessPathBase {
        override fun toString(): String = "const($constant)"
    }

    object Return : AccessPathBase {
        override fun toString(): String = "<return>"
    }
}

fun EtsValue.toBase(): AccessPathBase = when (this) {
    is EtsConstant -> AccessPathBase.Const(this)
    is EtsLocal -> if (name == "this") AccessPathBase.This else AccessPathBase.Local(name)
    is EtsThis -> AccessPathBase.This
    is EtsParameterRef -> AccessPathBase.Arg(index)
    else -> error("$this is not access path base")
}

fun EtsEntity.toPathOrNull(): AccessPath? = when (this) {
    is EtsConstant -> AccessPath(toBase(), emptyList())

    is EtsLocal -> AccessPath(toBase(), emptyList())

    is EtsThis -> AccessPath(toBase(), emptyList())

    is EtsParameterRef -> AccessPath(toBase(), emptyList())

    is EtsArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is EtsInstanceFieldRef -> {
        instance.toPathOrNull()?.let {
            it + FieldAccessor(field.name)
        }
    }

    is EtsStaticFieldRef -> {
        AccessPath(AccessPathBase.Static, listOf(FieldAccessor(field.name)))
    }

    is EtsCastExpr -> arg.toPathOrNull()

    else -> null
}

fun EtsEntity.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}
