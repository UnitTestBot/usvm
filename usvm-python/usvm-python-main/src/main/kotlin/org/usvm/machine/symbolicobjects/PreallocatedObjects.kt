package org.usvm.machine.symbolicobjects

import org.usvm.constraints.UPathConstraints
import org.usvm.language.PyCallable
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.PyContext
import org.usvm.machine.extractCurState
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.memory.UMemory

class PreallocatedObjects(
    val noneObject: UninterpretedSymbolicPythonObject,
    val trueObject: UninterpretedSymbolicPythonObject,
    val falseObject: UninterpretedSymbolicPythonObject,
    private val concreteStrToSymbol: MutableMap<String, UninterpretedSymbolicPythonObject>,
    private val symbolToConcreteStr: MutableMap<UninterpretedSymbolicPythonObject, String>,
    private val refOfString: MutableMap<String, PyObject>,
) {

    fun allocateStr(ctx: ConcolicRunContext, string: String, ref: PyObject): UninterpretedSymbolicPythonObject {
        requireNotNull(ctx.curState)
        val cached = concreteStrToSymbol[string]
        if (cached != null) {
            return cached
        }
        val result =
            constructEmptyStaticObject(ctx.ctx, ctx.extractCurState().memory, ctx.typeSystem, ctx.typeSystem.pythonStr)
        concreteStrToSymbol[string] = result
        symbolToConcreteStr[result] = string
        refOfString[string] = ref
        ConcretePythonInterpreter.incref(ref)
        return result
    }

    fun concreteString(symbol: UninterpretedSymbolicPythonObject): String? =
        symbolToConcreteStr[symbol]

    fun refOfString(string: String): PyObject? =
        refOfString[string]

    fun listAllocatedStrs(): List<UninterpretedSymbolicPythonObject> =
        symbolToConcreteStr.keys.toList()

    fun clone(): PreallocatedObjects =
        PreallocatedObjects(
            noneObject,
            trueObject,
            falseObject,
            concreteStrToSymbol.toMutableMap(),
            symbolToConcreteStr.toMutableMap(),
            refOfString.toMutableMap()
        )

    companion object {
        fun initialize(
            ctx: PyContext,
            initialMemory: UMemory<PythonType, PyCallable>,
            initialPathConstraints: UPathConstraints<PythonType>,
            typeSystem: PythonTypeSystem,
        ): PreallocatedObjects =
            PreallocatedObjects(
                noneObject = constructEmptyStaticObject(ctx, initialMemory, typeSystem, typeSystem.pythonNoneType),
                trueObject = constructInitialBool(ctx, initialMemory, initialPathConstraints, typeSystem, ctx.trueExpr),
                falseObject = constructInitialBool(
                    ctx,
                    initialMemory,
                    initialPathConstraints,
                    typeSystem,
                    ctx.falseExpr
                ),
                concreteStrToSymbol = mutableMapOf(),
                symbolToConcreteStr = mutableMapOf(),
                refOfString = mutableMapOf()
            )
    }
}
