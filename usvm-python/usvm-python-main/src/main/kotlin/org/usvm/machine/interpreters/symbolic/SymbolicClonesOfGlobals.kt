package org.usvm.machine.interpreters.symbolic

import org.usvm.annotations.ids.ApproximationId
import org.usvm.annotations.ids.SymbolicMethodId
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.utils.NamedSymbolForCPython
import org.usvm.machine.interpreters.concrete.utils.SymbolForCPython

object SymbolicClonesOfGlobals {
    private val clonesMap: MutableMap<String, SymbolForCPython> = mutableMapOf()

    fun restart() {
        clonesMap.clear()
        clonesMap["int"] =
            ConcretePythonInterpreter.constructPartiallyAppliedSymbolicMethod(null, SymbolicMethodId.Int)
        clonesMap["float"] =
            ConcretePythonInterpreter.constructPartiallyAppliedSymbolicMethod(null, SymbolicMethodId.Float)
        clonesMap["enumerate"] =
            ConcretePythonInterpreter.constructPartiallyAppliedSymbolicMethod(null, SymbolicMethodId.Enumerate)
        clonesMap["list"] =
            ConcretePythonInterpreter.constructApproximation(null, ApproximationId.ListConstructor)
        clonesMap["set"] =
            ConcretePythonInterpreter.constructApproximation(null, ApproximationId.SetConstructor)
        clonesMap["dict"] =
            ConcretePythonInterpreter.constructApproximation(null, ApproximationId.DictConstructor)
    }

    init {
        restart()
    }

    fun getNamedSymbols(): Array<NamedSymbolForCPython> =
        clonesMap.map { NamedSymbolForCPython(it.key, it.value) }.toTypedArray()
}
