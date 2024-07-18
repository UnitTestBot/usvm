package org.usvm.machine.model

import mu.KLogging
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.PyContext
import org.usvm.machine.PyState
import org.usvm.machine.types.ArrayLikeConcretePythonType
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.MockType
import org.usvm.machine.types.PythonType
import org.usvm.model.UModelBase
import org.usvm.types.TypesResult

fun UModelBase<PythonType>.toPyModel(
    ctx: PyContext,
    info: GivenPathConstraintsInfo,
): PyModel {
    if (this is PyModel) {
        return this
    }
    return PyModel(ctx, this, info)
}

class PyModelHolder(var model: PyModel)

fun substituteModel(state: PyState, newModel: PyModel, constraint: UBoolExpr, ctx: ConcolicRunContext) {
    state.models = listOf(newModel)
    state.pathConstraints += constraint
    ctx.modelHolder.model = newModel
}

fun PyModel.getConcreteType(address: UConcreteHeapRef): ConcretePythonType? {
    val typeStream = types.getTypeStream(address)
    val prefix = typeStream.take(2)
    if (prefix !is TypesResult.SuccessfulTypesResult || prefix.size > 1) {
        return null
    }
    return prefix.first() as? ConcretePythonType
}

fun PyModel.getFirstType(address: UConcreteHeapRef): PythonType? {
    val typeStream = types.getTypeStream(address).take(1)
    if (typeStream !is TypesResult.SuccessfulTypesResult || typeStream.types.isEmpty()) {
        return null
    }
    val first = typeStream.take(1).first()
    val concrete = getConcreteType(address)
    if (concrete == null) {
        if (first is ArrayLikeConcretePythonType) {
            logger.info("Here! (ArrayLikeConcretePythonType)")
        }
        if (first !is MockType) {
            logger.error(
                "TypeStream starting with $first instead of mock"
            ) // TODO: supports mocks with different sets of methods
            return null
        }
    }
    return first
}

private val logger = object : KLogging() {}.logger
