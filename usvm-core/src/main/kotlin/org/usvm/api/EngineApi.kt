package org.usvm.api

import org.usvm.UBoolExpr
import org.usvm.UHeapRef
import org.usvm.UState


fun UState<*, *, *, *, *, *>.assume(expr: UBoolExpr) {
    pathConstraints += expr
}

fun UState<*, *, *, *, *, *>.objectTypeEquals(lhs: UHeapRef, rhs: UHeapRef): UBoolExpr {
    TODO("Objects types equality check: $lhs, $rhs")
}
