package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsType
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope

/** Retains the cast semantics used by ETS analysis unless assertion erasure is explicitly enabled. */
internal fun TsContext.applyCastConstraints(scope: TsStepScope, value: UExpr<*>, type: EtsType): UExpr<*> {
    if (value is UConcreteHeapRef && classConstructorSignature(value) != null) return value

    return when (value.sort) {
        fp64Sort -> TODO("Not yet implemented https://github.com/UnitTestBot/usvm/issues/299")
        boolSort -> TODO("Not yet implemented https://github.com/UnitTestBot/usvm/issues/299")
        addressSort -> scope.calcOnState {
            val instance = value.asExpr(addressSort)
            if (instance.isFakeObject()) {
                val fakeType = instance.getFakeType(scope)
                pathConstraints += fakeType.refTypeExpr
                val refValue = instance.extractRef(scope)
                pathConstraints += memory.types.evalIsSubtype(refValue, type)
                return@calcOnState instance
            }

            if (type !is EtsRefType) {
                TODO("Not supported yet https://github.com/UnitTestBot/usvm/issues/299")
            }

            pathConstraints += memory.types.evalIsSubtype(instance, type)
            instance
        }
        else -> error("Unsupported cast to $type from sort ${value.sort}")
    }
}
