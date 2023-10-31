package org.usvm.merging

import org.usvm.UBoolExpr
import org.usvm.UContext

interface MergeGuard {
    val thisConstraint: UBoolExpr

    val otherConstraint: UBoolExpr
}

class MutableMergeGuard(
    private val ctx: UContext<*>,
) : MergeGuard {
    private val _thisConstraints = mutableListOf<UBoolExpr>()
    private val _otherConstraints = mutableListOf<UBoolExpr>()

    private var _thisConstraint: UBoolExpr? = null
    override val thisConstraint: UBoolExpr
        get() = _thisConstraint ?: ctx.mkAnd(_thisConstraints).also { _thisConstraint = it }

    private var _otherConstraint: UBoolExpr? = null
    override val otherConstraint: UBoolExpr
        get() = _otherConstraint ?: ctx.mkAnd(_otherConstraints).also { _otherConstraint = it }

    fun appendThis(constraints: Sequence<UBoolExpr>) {
        _thisConstraints.addAll(constraints)
        _thisConstraint = null
    }

    fun appendOther(constraints: Sequence<UBoolExpr>) {
        _otherConstraints.addAll(constraints)
        _otherConstraint = null
    }
}