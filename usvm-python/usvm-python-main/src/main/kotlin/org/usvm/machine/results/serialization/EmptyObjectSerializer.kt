package org.usvm.machine.results.serialization

import org.usvm.machine.interpreters.concrete.PyObject

object EmptyObjectSerializer : PythonObjectSerializer<Unit> {
    override fun serialize(obj: PyObject) = run {}
}
