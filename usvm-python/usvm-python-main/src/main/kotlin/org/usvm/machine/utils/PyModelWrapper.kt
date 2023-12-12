package org.usvm.machine.utils

import io.ksmt.expr.KInterpretedValue
import org.usvm.*
import org.usvm.api.readField
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.symbolicobjects.PropertyOfPythonObject
import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.PythonType
import org.usvm.language.types.MockType
import org.usvm.machine.PythonExecutionState
import org.usvm.model.UModelBase
import org.usvm.types.TypesResult

class PyModelWrapper(val uModel: UModelBase<PythonType>) {
    fun <Sort : USort> eval(expr: UExpr<Sort>): KInterpretedValue<Sort> =
        uModel.eval(expr) as KInterpretedValue<Sort>

    fun <Sort : USort> readField(ref: UConcreteHeapRef, field: PropertyOfPythonObject, sort: Sort): KInterpretedValue<Sort> =
        uModel.readField(ref, field, sort) as KInterpretedValue<Sort>

    override fun equals(other: Any?): Boolean {
        if (other !is PyModelWrapper)
            return false
        return uModel == other.uModel
    }

    override fun hashCode(): Int {
        return uModel.hashCode()
    }

    fun getFirstType(ref: UConcreteHeapRef): PythonType? {
        val typeStream = uModel.types.getTypeStream(ref).take(1)
        if (typeStream !is TypesResult.SuccessfulTypesResult || typeStream.types.isEmpty())
            return null
        val first = typeStream.take(1).first()
        val concrete = getConcreteType(ref)
        if (concrete == null)
            require(first is MockType)
        return first
    }

    fun getConcreteType(ref: UConcreteHeapRef): ConcretePythonType? {
        val typeStream = uModel.types.getTypeStream(ref)
        val prefix = typeStream.take(2)
        if (prefix !is TypesResult.SuccessfulTypesResult || prefix.size > 1)
            return null
        return prefix.first() as? ConcretePythonType
    }
}

class PyModelHolder(var model: PyModelWrapper)

fun substituteModel(state: PythonExecutionState, newModel: PyModelWrapper, constraint: UBoolExpr, ctx: ConcolicRunContext) {
    state.models = listOf(newModel.uModel)
    state.pathConstraints += constraint
    ctx.modelHolder.model = newModel
}