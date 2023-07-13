package org.usvm.interpreter

import io.ksmt.expr.KInterpretedValue
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.language.PropertyOfPythonObject
import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.PythonType
import org.usvm.model.UModelBase

@Suppress("unchecked_cast")
class PyModel(val uModel: UModelBase<PropertyOfPythonObject, PythonType>) {
    fun <Sort : USort> eval(expr: UExpr<Sort>): KInterpretedValue<Sort> =
        uModel.eval(expr) as KInterpretedValue<Sort>

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

    fun getConcreteType(ref: UConcreteHeapRef): ConcretePythonType? {
        val typeStream = uModel.types.typeStream(ref)
        val prefix = typeStream.take(2)
        if (prefix.size > 1)
            return null
        return prefix.first() as? ConcretePythonType
    }
}