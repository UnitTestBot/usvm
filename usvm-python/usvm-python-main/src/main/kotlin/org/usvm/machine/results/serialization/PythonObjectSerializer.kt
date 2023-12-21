package org.usvm.machine.results.serialization

import org.usvm.machine.interpreters.concrete.PythonObject

abstract class PythonObjectSerializer<PythonObjectRepresentation> {
    abstract fun serialize(obj: PythonObject): PythonObjectRepresentation
}