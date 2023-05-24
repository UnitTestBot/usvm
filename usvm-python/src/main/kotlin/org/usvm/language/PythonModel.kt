package org.usvm.language

import org.usvm.UExpr
import org.usvm.USort
import org.usvm.model.UModel

class PythonModel(): UModel {
    override fun <Sort : USort> eval(expr: UExpr<Sort>): UExpr<Sort> {
        TODO("Not yet implemented")
    }
}