package org.usvm.language

import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonNamespace
import org.usvm.machine.interpreters.emptyNamespace
import org.usvm.utils.withAdditionalPaths
import java.io.File

sealed class PythonProgram(val additionalPaths: Set<File>) {
    abstract fun <T> withPinnedCallable(
        callable: PythonUnpinnedCallable,
        block: (PythonPinnedCallable) -> T
    ): T
}

class PrimitivePythonProgram internal constructor(
    private val namespace: PythonNamespace,
    additionalPaths: Set<File>
): PythonProgram(additionalPaths) {
    override fun <T> withPinnedCallable(
        callable: PythonUnpinnedCallable,
        block: (PythonPinnedCallable) -> T
    ): T {
        require(callable.module == null)
        val pinned = PythonPinnedCallable(callable.reference(namespace))
        return block(pinned)
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
    override fun <T> withPinnedCallable(
        callable: PythonUnpinnedCallable,
        block: (PythonPinnedCallable) -> T
    ): T = withAdditionalPaths(roots) {
        if (callable.module == null) {
            val pinned = PythonPinnedCallable(callable.reference(emptyNamespace))  // for lambdas
            block(pinned)
        } else {
            val namespace = getNamespaceOfModule(callable.module)
            val pinned = PythonPinnedCallable(callable.reference(namespace))
            block(pinned)
        }
    }

    fun getNamespaceOfModule(module: String): PythonNamespace {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.concreteRun(namespace, "import sys")
        module.split(".").fold("") { acc, name ->
            val curModule = acc + name
            ConcretePythonInterpreter.concreteRun(namespace, "import $curModule")
            "$acc$name."
        }
        val resultAsObj = ConcretePythonInterpreter.eval(namespace, "$module.__dict__")
        require(ConcretePythonInterpreter.getPythonObjectTypeName(resultAsObj) == "dict")
        return PythonNamespace(resultAsObj.address)
    }

    fun getPrimitiveProgram(module: String): PrimitivePythonProgram = withAdditionalPaths(roots) {
        val namespace = getNamespaceOfModule(module)
        PrimitivePythonProgram(namespace, roots)
    }
}
