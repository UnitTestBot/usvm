package org.usvm.util

import io.ksmt.sort.KFp64Sort
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.machine.TSContext

// Built-in KContext.bvToBool has identical implementation.
fun TSContext.boolToFpSort(expr: UExpr<UBoolSort>): UExpr<KFp64Sort> =
    mkIte(expr, mkFp64(1.0), mkFp64(0.0))

/**
 * It is not a cast. It's a function that builds logical condition, e.g., for if statement.
 *
 * Note that 0.1 != false and 0.1 != true, so this operation must not be used for comparison of fp and bool values.
 */
fun TSContext.fpToBoolForConditions(expr: UExpr<KFp64Sort>) =
    mkIte(mkFpEqualExpr(expr, mkFp64(0.0)), mkFalse(), mkTrue())
