package org.usvm.language.types

object PythonAnyType: VirtualPythonType()

sealed class TypeProtocol: VirtualPythonType()

object HasNbBool: TypeProtocol()