package org.usvm.language

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter.emptyNamespace
import org.usvm.machine.interpreters.concrete.PyNamespace
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.machine.utils.withAdditionalPaths
import java.io.File

sealed class PyProgram(val additionalPaths: Set<File>) {
    abstract fun <T> withPinnedCallable(
        callable: PyUnpinnedCallable,
        typeSystem: PythonTypeSystem,
        block: (PyPinnedCallable) -> T,
    ): T
}

class PrimitivePyProgram internal constructor(
    private val namespaceGetter: () -> PyNamespace,
    additionalPaths: Set<File>,
) : PyProgram(additionalPaths) {
    override fun <T> withPinnedCallable(
        callable: PyUnpinnedCallable,
        typeSystem: PythonTypeSystem,
        block: (PyPinnedCallable) -> T,
    ): T {
        require(callable.module == null)
        val namespace = namespaceGetter()
        val pinned = PyPinnedCallable(callable.reference(namespace))
        return block(pinned)
    }

    companion object {
        fun fromString(asString: String): PrimitivePyProgram {
            val namespaceGetter = {
                val namespace = ConcretePythonInterpreter.getNewNamespace()
                ConcretePythonInterpreter.concreteRun(namespace, asString, setHook = true)
                namespace
            }
            return PrimitivePyProgram(namespaceGetter, emptySet())
        }
    }
}

class StructuredPyProgram(val roots: Set<File>) : PyProgram(roots) {
    override fun <T> withPinnedCallable(
        callable: PyUnpinnedCallable,
        typeSystem: PythonTypeSystem,
        block: (PyPinnedCallable) -> T,
    ): T = withAdditionalPaths(roots, typeSystem) {
        if (callable.module == null) {
            val pinned = PyPinnedCallable(callable.reference(emptyNamespace)) // for lambdas
            block(pinned)
        } else {
            val namespace = getNamespaceOfModule(callable.module)
            requireNotNull(namespace) { "Couldn't get namespace of function module" }
            val pinned = PyPinnedCallable(callable.reference(namespace))
            block(pinned)
        }
    }

    fun getNamespaceOfModule(module: String): PyNamespace? {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.concreteRun(namespace, "import sys")
        module.split(".").fold("") { acc, name ->
            val curModule = acc + name
            ConcretePythonInterpreter.concreteRun(namespace, "import $curModule", setHook = true)
            "$acc$name."
        }
        val resultAsObj = ConcretePythonInterpreter.eval(namespace, "$module.__dict__")
        if (ConcretePythonInterpreter.getPythonObjectTypeName(resultAsObj) != "dict") {
            return null
        }
        return PyNamespace(resultAsObj.address)
    }

    fun getPrimitiveProgram(module: String): PrimitivePyProgram {
        val namespaceGetter = {
            withAdditionalPaths(roots, null) {
                getNamespaceOfModule(module) ?: error("Couldn't get namespace of module")
            }
        }
        return PrimitivePyProgram(namespaceGetter, roots)
    }
}
