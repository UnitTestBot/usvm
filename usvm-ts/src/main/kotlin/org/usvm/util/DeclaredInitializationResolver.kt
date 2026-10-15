package org.usvm.util

import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.usvm.machine.TsVirtualMethodCallStmt

/** Construction and instance initialization use their declared owner, including symbolic receivers. */
internal fun TsVirtualMethodCallStmt.resolveDeclaredInitialization(hierarchy: EtsHierarchy): EtsMethod? {
    val callee = call.callee
    val isInitialization = callee.name == CONSTRUCTOR_NAME || callee.name == INSTANCE_INIT_METHOD_NAME
    if (targetCaptured || !isInitialization) return null

    val ownerType = EtsClassType(callee.enclosingClass)
    val methods = hierarchy.classesForType(ownerType)
        .flatMap { owner -> owner.methods }
        .filter { method -> method.name == callee.name }
        .canonicalizeExecutableOverloads()

    return methods.singleOrNull()
}
