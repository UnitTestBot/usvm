package org.usvm.util

import io.ksmt.sort.KFp64Sort
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.machine.TSContext

// Built-in KContext.bvToBool has identical implementation.
fun TSContext.boolToFpSort(expr: UExpr<UBoolSort>): UExpr<KFp64Sort> =
    mkIte(expr, mkFp64(1.0), mkFp64(0.0))
