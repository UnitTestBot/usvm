package org.usvm.language

import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonNamespace
import org.usvm.machine.interpreters.emptyNamespace
import org.usvm.utils.withAdditionalPaths
import java.io.File

sealed class PythonProgram(val additionalPaths: Set<File>) {
    abstract fun pinCallable(callable: PythonUnpinnedCallable): PythonPinnedCallable
}

class PrimitivePythonProgram internal constructor(
    private val namespace: PythonNamespace,
    additionalPaths: Set<File>
): PythonProgram(additionalPaths) {
    override fun pinCallable(callable: PythonUnpinnedCallable): PythonPinnedCallable {
        require(callable.module == null)
        return PythonPinnedCallable(callable.reference(namespace))
    }

    companion object {
        fun fromString(asString: String): PrimitivePythonProgram {
            val namespace = ConcretePythonInterpreter.getNewNamespace()
            ConcretePythonInterpreter.concreteRun(namespace, asString)
            return PrimitivePythonProgram(namespace, emptySet())
        }
    }
}

class StructuredPythonProgram(private val roots: Set<File>): PythonProgram(roots) {
    private fun getNamespaceOfModule(module: String): PythonNamespace =
        withAdditionalPaths(roots) {
            val namespace = ConcretePythonInterpreter.getNewNamespace()
            ConcretePythonInterpreter.concreteRun(namespace, "import sys")
            module.split(".").fold("") { acc, name ->
                val curModule = acc + name
                ConcretePythonInterpreter.concreteRun(namespace, "import $curModule")
                "$acc$name."
            }
            val resultAsObj = ConcretePythonInterpreter.eval(namespace, "$module.__dict__")
            require(ConcretePythonInterpreter.getPythonObjectTypeName(resultAsObj) == "dict")
            PythonNamespace(resultAsObj.address)
        }

    override fun pinCallable(callable: PythonUnpinnedCallable): PythonPinnedCallable {
        if (callable.module == null) {
            return PythonPinnedCallable(callable.reference(emptyNamespace))  // for lambdas
        }
        val requiredNamespace = getNamespaceOfModule(callable.module)
        return PythonPinnedCallable(callable.reference(requiredNamespace))
    }

    fun getPrimitiveProgram(module: String): PrimitivePythonProgram {
        val namespace = getNamespaceOfModule(module)
        return PrimitivePythonProgram(namespace, roots)
    }
}
