package org.usvm.utils

import org.usvm.UExpr
import org.usvm.USort
import org.usvm.model.UModel
import org.usvm.sampleUValue

fun <Sort : USort> UModel?.eval(expr: UExpr<Sort>): UExpr<Sort> = this?.eval(expr) ?: expr.sort.sampleUValue()
