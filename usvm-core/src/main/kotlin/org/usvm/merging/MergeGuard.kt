package org.usvm.merging

import org.usvm.UBoolExpr
import org.usvm.UContext

interface MergeGuard {
    val leftConstraint: UBoolExpr

    val rightConstraint: UBoolExpr
}

class MutableMergeGuard(
    private val ctx: UContext<*>,
) : MergeGuard {
    private val _leftConstraints = mutableListOf<UBoolExpr>()
    private val _rightConstraints = mutableListOf<UBoolExpr>()

    private var _leftConstraint: UBoolExpr? = null
    override val leftConstraint: UBoolExpr
        get() = _leftConstraint ?: ctx.mkAnd(_leftConstraints).also { _leftConstraint = it }

    private var _rightConstraint: UBoolExpr? = null
    override val rightConstraint: UBoolExpr
        get() = _rightConstraint ?: ctx.mkAnd(_rightConstraints).also { _rightConstraint = it }

    fun appendLeft(constraints: Sequence<UBoolExpr>) {
        _leftConstraints.addAll(constraints)
        _leftConstraint = null
    }

    fun appendRight(constraints: Sequence<UBoolExpr>) {
        _rightConstraints.addAll(constraints)
        _rightConstraint = null
    }
}