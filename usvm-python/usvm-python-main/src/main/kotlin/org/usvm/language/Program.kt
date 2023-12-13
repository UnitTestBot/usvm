package org.usvm.language

import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter.emptyNamespace
import org.usvm.machine.interpreters.concrete.PythonNamespace
import org.usvm.machine.utils.withAdditionalPaths
import java.io.File

sealed class PythonProgram(val additionalPaths: Set<File>) {
    abstract fun <T> withPinnedCallable(
        callable: PythonUnpinnedCallable,
        typeSystem: PythonTypeSystem,
        block: (PythonPinnedCallable) -> T
    ): T
}

class PrimitivePythonProgram internal constructor(
    private val namespaceGetter: () -> PythonNamespace,
    additionalPaths: Set<File>
): PythonProgram(additionalPaths) {
    override fun <T> withPinnedCallable(
        callable: PythonUnpinnedCallable,
        typeSystem: PythonTypeSystem,
        block: (PythonPinnedCallable) -> T
    ): T {
        require(callable.module == null)
        val namespace = namespaceGetter()
        val pinned = PythonPinnedCallable(callable.reference(namespace))
        return block(pinned)
    }

    companion object {
        fun fromString(asString: String): PrimitivePythonProgram {
            val namespaceGetter = {
                val namespace = ConcretePythonInterpreter.getNewNamespace()
                ConcretePythonInterpreter.concreteRun(namespace, asString, setHook = true)
                namespace
            }
            return PrimitivePythonProgram(namespaceGetter, emptySet())
        }
    }
}

class StructuredPythonProgram(val roots: Set<File>): PythonProgram(roots) {
    override fun <T> withPinnedCallable(
        callable: PythonUnpinnedCallable,
        typeSystem: PythonTypeSystem,
        block: (PythonPinnedCallable) -> T
    ): T = withAdditionalPaths(roots, typeSystem) {
        if (callable.module == null) {
            val pinned = PythonPinnedCallable(callable.reference(emptyNamespace))  // for lambdas
            block(pinned)
        } else {
            val namespace = getNamespaceOfModule(callable.module) ?: error("Couldn't get namespace of function module")
            val pinned = PythonPinnedCallable(callable.reference(namespace))
            block(pinned)
        }
    }

    fun getNamespaceOfModule(module: String): PythonNamespace? {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.concreteRun(namespace, "import sys")
        module.split(".").fold("") { acc, name ->
            val curModule = acc + name
            ConcretePythonInterpreter.concreteRun(namespace, "import $curModule", setHook = true)
            "$acc$name."
        }
        val resultAsObj = ConcretePythonInterpreter.eval(namespace, "$module.__dict__")
        //println(module)
        if (ConcretePythonInterpreter.getPythonObjectTypeName(resultAsObj) != "dict")
            return null
        return PythonNamespace(resultAsObj.address)
    }

    fun getPrimitiveProgram(module: String): PrimitivePythonProgram {
        val namespaceGetter = {
            withAdditionalPaths(roots, null) {
                getNamespaceOfModule(module) ?: error("Couldn't get namespace of module")
            }
        }
        return PrimitivePythonProgram(namespaceGetter, roots)
    }
}
