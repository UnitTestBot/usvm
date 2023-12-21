package org.usvm.machine.results.serialization

import org.usvm.machine.interpreters.concrete.PythonObject

object EmptyObjectSerializer: PythonObjectSerializer<Unit>() {
    override fun serialize(obj: PythonObject) = run {}
}