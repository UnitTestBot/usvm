package org.usvm.machine.results.observers

import org.usvm.machine.interpreters.concrete.PyObject

abstract class InputPythonObjectObserver {
    abstract fun onInputObjects(inputObjects: List<PyObject>)
}

object EmptyInputPythonObjectObserver : InputPythonObjectObserver() {
    override fun onInputObjects(inputObjects: List<PyObject>) = run {}
}
