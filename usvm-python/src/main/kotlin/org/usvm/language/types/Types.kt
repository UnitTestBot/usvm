package org.usvm.language.types

import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonObject
import org.usvm.interpreter.emptyNamespace
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem

sealed class PythonType

open class VirtualPythonType: PythonType()

data class ConcretePythonType(val typeName: String, val asObject: PythonObject): PythonType()

private fun createConcreteType(name: String) = ConcretePythonType(name, ConcretePythonInterpreter.eval(emptyNamespace, name))

val pythonInt = createConcreteType("int")
val pythonBool = createConcreteType("bool")
val pythonObjectType = createConcreteType("object")
val pythonNoneType = ConcretePythonType("NoneType", ConcretePythonInterpreter.eval(emptyNamespace, "type(None)"))
val pythonTypeType = createConcreteType("type")