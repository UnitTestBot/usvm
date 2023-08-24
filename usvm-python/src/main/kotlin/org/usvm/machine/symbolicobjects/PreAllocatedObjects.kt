package org.usvm.machine.symbolicobjects

import org.usvm.constraints.UPathConstraints
import org.usvm.language.PropertyOfPythonObject
import org.usvm.language.PythonCallable
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.UPythonContext
import org.usvm.memory.UMemoryBase

class PreAllocatedObjects(
    ctx: UPythonContext,
    initialMemory: UMemoryBase<PropertyOfPythonObject, PythonType, PythonCallable>,
    initialPathConstraints: UPathConstraints<PythonType, UPythonContext>,
    typeSystem: PythonTypeSystem
) {
    val noneObject = constructNone(initialMemory, typeSystem)
    val trueObject = constructInitialBool(ctx, initialMemory, initialPathConstraints, typeSystem, ctx.trueExpr)
    val falseObject = constructInitialBool(ctx, initialMemory, initialPathConstraints, typeSystem, ctx.falseExpr)
}