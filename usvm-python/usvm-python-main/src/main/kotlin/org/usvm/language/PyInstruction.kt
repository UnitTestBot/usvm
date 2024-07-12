package org.usvm.language

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject

data class PyInstruction(
    val numberInBytecode: Int,
    val code: PyObject,
)

fun extractInstructionsFromCode(code: PyObject): List<PyInstruction> {
    require(ConcretePythonInterpreter.getPythonObjectTypeName(code) == "code") {
        "Can extract instructions only from 'code' object"
    }
    val namespace = ConcretePythonInterpreter.getNewNamespace()
    ConcretePythonInterpreter.addObjectToNamespace(namespace, code, "f")
    ConcretePythonInterpreter.concreteRun(namespace, "import dis")
    val raw = ConcretePythonInterpreter.eval(
        namespace,
        "[x.offset for x in dis.Bytecode(f) if x.opname != 'RESUME']"
    )
    val rawStr = ConcretePythonInterpreter.getPythonObjectRepr(raw)
    return rawStr
        .removePrefix("[")
        .removeSuffix("]")
        .split(", ")
        .map {
            val offset = it.toInt()
            PyInstruction(offset, code)
        }.also {
            ConcretePythonInterpreter.decref(namespace)
        }
}
