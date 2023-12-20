package org.usvm.machine.results.observers

import org.usvm.machine.interpreters.concrete.PythonObject

abstract class InputPythonObjectObserver {
    abstract fun onInputObjects(inputObjects: List<PythonObject>)
}

object EmptyInputPythonObjectObserver: InputPythonObjectObserver() {
    override fun onInputObjects(inputObjects: List<PythonObject>) = run {}
}