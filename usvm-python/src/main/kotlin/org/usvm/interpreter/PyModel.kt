package org.usvm.interpreter

import io.ksmt.expr.KInterpretedValue
import org.usvm.*
import org.usvm.language.PropertyOfPythonObject
import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.PythonType
import org.usvm.language.types.TypeOfVirtualObject
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

    fun getFirstType(ref: UConcreteHeapRef): PythonType? {
        val typeStream = uModel.types.typeStream(ref)
        if (typeStream.isEmpty)
            return null
        val first = typeStream.take(1).first()
        val concrete = getConcreteType(ref)
        if (concrete == null)
            require(first is TypeOfVirtualObject)
        return first
    }

    fun getConcreteType(ref: UConcreteHeapRef): ConcretePythonType? {
        val typeStream = uModel.types.typeStream(ref)
        val prefix = typeStream.take(2)
        if (prefix.size > 1)
            return null
        return prefix.first() as? ConcretePythonType
    }
}

class PyModelHolder(var model: PyModel)

fun substituteModel(state: PythonExecutionState, newModel: PyModel, ctx: ConcolicRunContext) {
    state.models = listOf(newModel.uModel)
    ctx.modelHolder.model = newModel
}