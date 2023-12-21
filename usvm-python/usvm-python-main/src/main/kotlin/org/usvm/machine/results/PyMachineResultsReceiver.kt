package org.usvm.machine.results

import org.usvm.machine.results.observers.*
import org.usvm.machine.results.serialization.PythonObjectSerializer

interface PyMachineResultsReceiver<PyObjectRepr> {
    val serializer: PythonObjectSerializer<PyObjectRepr>
    val newStateObserver: NewStateObserver
    val inputModelObserver: InputModelObserver
    val inputPythonObjectObserver: InputPythonObjectObserver
    val pyTestObserver: PyTestObserver<PyObjectRepr>
}

class DefaultPyMachineResultsReceiver<PyObjectRepr>(
    override val serializer: PythonObjectSerializer<PyObjectRepr>
): PyMachineResultsReceiver<PyObjectRepr> {
    override val newStateObserver: NewStateObserver = EmptyNewStateObserver
    override val inputModelObserver: InputModelObserver = EmptyInputModelObserver
    override val inputPythonObjectObserver: InputPythonObjectObserver = EmptyInputPythonObjectObserver
    override val pyTestObserver: DefaultPyTestObserver<PyObjectRepr> = DefaultPyTestObserver()
}