package org.usvm.machine.symbolicobjects

import org.usvm.constraints.UPathConstraints
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.PythonCallable
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.UPythonContext
import org.usvm.memory.UMemory

class PreallocatedObjects(
    val noneObject: UninterpretedSymbolicPythonObject,
    val trueObject: UninterpretedSymbolicPythonObject,
    val falseObject: UninterpretedSymbolicPythonObject,
    private val concreteStrToSymbol: MutableMap<String, UninterpretedSymbolicPythonObject>,
    private val symbolToConcreteStr: MutableMap<UninterpretedSymbolicPythonObject, String>
) {

    fun allocateStr(ctx: ConcolicRunContext, string: String): UninterpretedSymbolicPythonObject {
        require(ctx.curState != null)
        val cached = concreteStrToSymbol[string]
        if (cached != null)
            return cached
        val result = constructEmptyObject(ctx.ctx, ctx.curState!!.memory, ctx.typeSystem, ctx.typeSystem.pythonStr)
        concreteStrToSymbol[string] = result
        symbolToConcreteStr[result] = string
        return result
    }

    fun concreteString(symbol: UninterpretedSymbolicPythonObject): String? =
        symbolToConcreteStr[symbol]

    fun clone(): PreallocatedObjects =
        PreallocatedObjects(
            noneObject,
            trueObject,
            falseObject,
            concreteStrToSymbol.toMutableMap(),
            symbolToConcreteStr.toMutableMap()
        )

    companion object {
        fun initialize(
            ctx: UPythonContext,
            initialMemory: UMemory<PythonType, PythonCallable>,
            initialPathConstraints: UPathConstraints<PythonType>,
            typeSystem: PythonTypeSystem
        ): PreallocatedObjects =
            PreallocatedObjects(
                noneObject = constructEmptyObject(ctx, initialMemory, typeSystem, typeSystem.pythonNoneType),
                trueObject = constructInitialBool(ctx, initialMemory, initialPathConstraints, typeSystem, ctx.trueExpr),
                falseObject = constructInitialBool(ctx, initialMemory, initialPathConstraints, typeSystem, ctx.falseExpr),
                concreteStrToSymbol = mutableMapOf(),
                symbolToConcreteStr = mutableMapOf()
            )
    }
}