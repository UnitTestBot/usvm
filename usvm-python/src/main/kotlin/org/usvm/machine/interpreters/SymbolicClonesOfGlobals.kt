package org.usvm.machine.interpreters

import org.usvm.language.NamedSymbolForCPython
import org.usvm.language.SymbolForCPython

object SymbolicClonesOfGlobals {
    private val clonesMap: MutableMap<String, SymbolForCPython> = mutableMapOf()

    fun restart() {
        clonesMap.clear()
        clonesMap["int"] = SymbolForCPython(null, ConcretePythonInterpreter.intConstructorRef)
        clonesMap["float"] = SymbolForCPython(null, ConcretePythonInterpreter.floatConstructorRef)
    }

    init {
        restart()
    }

    fun getNamedSymbols(): Array<NamedSymbolForCPython> =
        clonesMap.map { NamedSymbolForCPython(it.key, it.value) }.toTypedArray()
}