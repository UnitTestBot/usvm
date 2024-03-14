package org.usvm.machine.results.serialization

import org.usvm.machine.interpreters.concrete.PyObject

abstract class PythonObjectSerializer<PythonObjectRepresentation> {
    abstract fun serialize(obj: PyObject): PythonObjectRepresentation
}
