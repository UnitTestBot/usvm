package org.usvm.interpreter

import io.ksmt.expr.KInterpretedValue
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.language.PropertyOfPythonObject
import org.usvm.language.PythonType
import org.usvm.model.UModelBase

@Suppress("unchecked_cast")
class PyModel(val uModel: UModelBase<PropertyOfPythonObject, PythonType>) {
    fun <Sort : USort> eval(expr: UExpr<Sort>): UExpr<Sort> =
        uModel.eval(expr) // as KInterpretedValue<Sort>

    fun <Sort : USort> readField(ref: UConcreteHeapRef, field: PropertyOfPythonObject, sort: Sort): KInterpretedValue<Sort> =
        uModel.heap.readField(ref, field, sort) as KInterpretedValue<Sort>

    override fun equals(other: Any?): Boolean {
        if (other !is PyModel)
            return false
        return uModel == other.uModel
    }

    override fun hashCode(): Int {
        return uModel.hashCode()
    }
}