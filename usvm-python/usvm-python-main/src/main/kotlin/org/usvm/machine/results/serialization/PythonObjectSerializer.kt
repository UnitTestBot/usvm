package org.usvm.machine.results.serialization

import org.usvm.machine.interpreters.concrete.PyObject

interface PythonObjectSerializer<PythonObjectRepresentation> {
    fun serialize(obj: PyObject): PythonObjectRepresentation
}
