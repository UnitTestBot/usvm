package org.usvm.machine.interpreters

import org.usvm.annotations.ids.SymbolicMethodId
import org.usvm.language.NamedSymbolForCPython
import org.usvm.language.SymbolForCPython

object SymbolicClonesOfGlobals {
    private val clonesMap: MutableMap<String, SymbolForCPython> = mutableMapOf()

    fun restart() {
        clonesMap.clear()
        clonesMap["int"] =
            ConcretePythonInterpreter.constructPartiallyAppliedSymbolicMethod(null, SymbolicMethodId.Int)
        clonesMap["float"] =
            ConcretePythonInterpreter.constructPartiallyAppliedSymbolicMethod(null, SymbolicMethodId.Float)
    }

    init {
        restart()
    }

    fun getNamedSymbols(): Array<NamedSymbolForCPython> =
        clonesMap.map { NamedSymbolForCPython(it.key, it.value) }.toTypedArray()
}