package org.usvm.machine.results.observers

import org.usvm.machine.interpreters.concrete.PyObject

interface InputPythonObjectObserver {
    fun onInputObjects(inputObjects: List<PyObject>)
}

object EmptyInputPythonObjectObserver : InputPythonObjectObserver {
    override fun onInputObjects(inputObjects: List<PyObject>) = run {}
}
