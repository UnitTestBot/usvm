package org.usvm.util

import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.machine.TsVirtualMethodCallStmt

/** New and super calls use their declared owner; captured property calls retain virtual dispatch. */
internal fun TsVirtualMethodCallStmt.resolveDeclaredConstructor(hierarchy: EtsHierarchy): EtsMethod? {
    val callee = call.callee
    if (targetCaptured || callee.name != CONSTRUCTOR_NAME) return null

    val ownerType = EtsClassType(callee.enclosingClass)
    val constructors = hierarchy.classesForType(ownerType)
        .flatMap { owner -> owner.methods }
        .filter { method -> method.name == CONSTRUCTOR_NAME }
        .canonicalizeExecutableOverloads()

    return constructors.singleOrNull()
}
