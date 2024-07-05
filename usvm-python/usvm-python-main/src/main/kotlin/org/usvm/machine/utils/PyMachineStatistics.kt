package org.usvm.machine.utils

import org.usvm.language.PyPinnedCallable
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.interpreters.symbolic.operations.tracing.NextInstruction

// TODO [usvm-core]: replace with abstract statistics

fun writeLostSymbolicValuesReport(lostValues: Map<MethodDescription, Int>): String {
    val result = StringBuilder()
    lostValues.toList().sortedBy { -it.second }.forEach { (description, count) ->
        val intValue = description.description.toIntOrNull()
        val msg = if (intValue != null) {
            val namespace = ConcretePythonInterpreter.getNewNamespace()
            ConcretePythonInterpreter.concreteRun(namespace, "import dis")
            val ref = ConcretePythonInterpreter.eval(namespace, "dis.opname[$intValue]")
            ConcretePythonInterpreter.decref(namespace)
            ConcretePythonInterpreter.getPythonObjectRepr(ref)
        } else {
            description.description
        }
        result.append("$msg: $count\n")
    }
    return result.toString()
}

private fun addWithDefault(map: MutableMap<MethodDescription, Int>, descr: MethodDescription, value: Int = 1) {
    if (map[descr] == null) {
        map[descr] = 0
    }
    map[descr] = (map[descr] ?: error("$descr not in map")) + value
}

class PyMachineStatistics {
    val functionStatistics = mutableListOf<PythonMachineStatisticsOnFunction>()
    val meanCoverage: Double
        get() = functionStatistics.sumOf { it.coverage } / functionStatistics.size

    val meanCoverageNoVirtual: Double
        get() = functionStatistics.sumOf { it.coverageNoVirtual } / functionStatistics.size

    private val lostSymbolicValuesByOverallUsages: Map<MethodDescription, Int>
        get() {
            val map = mutableMapOf<MethodDescription, Int>()
            functionStatistics.forEach { functionStatistics ->
                functionStatistics.lostSymbolicValues.forEach {
                    addWithDefault(map, it.key, it.value)
                }
            }
            return map
        }

    private val lostSymbolicValuesByNumberOfFunctions: Map<MethodDescription, Int>
        get() {
            val map = mutableMapOf<MethodDescription, Int>()
            functionStatistics.forEach { functionStatistics ->
                functionStatistics.lostSymbolicValues.forEach {
                    addWithDefault(map, it.key)
                }
            }
            return map
        }

    private val numberOfFunctionsWithUnregisteredVirtualOperations: Int
        get() = functionStatistics.fold(0) { acc, cur ->
            acc + if (cur.numberOfUnregisteredVirtualOperations > 0) 1 else 0
        }

    fun writeReport(): String {
        val result = StringBuilder()
        result.append("Functions analyzed: ${functionStatistics.size}\n")
        result.append("Mean coverage: $meanCoverage\n")
        result.append("Mean coverage without virtual objects: $meanCoverageNoVirtual\n")
        result.append(
            "Number of functions with unregistered virtual operations: " +
                "$numberOfFunctionsWithUnregisteredVirtualOperations\n"
        )
        result.append("Lost symbolic values (by number of functions):\n")
        result.append(writeLostSymbolicValuesReport(lostSymbolicValuesByNumberOfFunctions))
        result.append("Lost symbolic values (by overall usages):\n")
        result.append(writeLostSymbolicValuesReport(lostSymbolicValuesByOverallUsages))
        return result.toString()
    }
}

class PythonMachineStatisticsOnFunction(private val function: PyPinnedCallable) {
    internal val lostSymbolicValues = mutableMapOf<MethodDescription, Int>()
    internal var numberOfUnregisteredVirtualOperations = 0
    fun addLostSymbolicValue(descr: MethodDescription) {
        addWithDefault(lostSymbolicValues, descr)
    }

    fun addUnregisteredVirtualOperation() {
        numberOfUnregisteredVirtualOperations += 1
    }

    private val instructionOffsets: List<Int> by lazy {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.addObjectToNamespace(namespace, function.pyObject, "f")
        ConcretePythonInterpreter.concreteRun(namespace, "import dis")
        val raw = ConcretePythonInterpreter.eval(
            namespace,
            "[x.offset for x in dis.Bytecode(f) if x.opname != 'RESUME']"
        )
        val rawStr = ConcretePythonInterpreter.getPythonObjectRepr(raw)
        rawStr
            .removePrefix("[")
            .removeSuffix("]")
            .split(", ")
            .map { it.toInt() }
            .also {
                ConcretePythonInterpreter.decref(namespace)
            }
    }
    var coverage: Double = 0.0
    var coverageNoVirtual: Double = 0.0
    private val coveredInstructions = mutableSetOf<Int>()
    private val coveredInstructionsNoVirtual = mutableSetOf<Int>()
    private val functionCode: PyObject by lazy {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.addObjectToNamespace(namespace, function.pyObject, "f")
        ConcretePythonInterpreter.eval(namespace, "f.__code__").also {
            ConcretePythonInterpreter.decref(namespace)
        }
    }
    fun updateCoverage(cmd: NextInstruction, usesVirtual: Boolean) {
        if (cmd.pyInstruction.code != functionCode) {
            return
        }
        coveredInstructions += cmd.pyInstruction.numberInBytecode
        if (!usesVirtual) {
            coveredInstructionsNoVirtual += cmd.pyInstruction.numberInBytecode
        }
        coverage = coveredInstructions.size.toDouble() / instructionOffsets.size
        coverageNoVirtual = coveredInstructionsNoVirtual.size.toDouble() / instructionOffsets.size
    }

    fun writeReport(): String {
        val result = StringBuilder()
        result.append("Coverage: $coverage\n")
        result.append("Coverage without virtual objects: $coverageNoVirtual\n")
        result.append("Lost symbolic values:\n")
        result.append("Number of unregistered virtual operations: $numberOfUnregisteredVirtualOperations\n")
        result.append(writeLostSymbolicValuesReport(lostSymbolicValues))
        return result.toString()
    }
}

data class MethodDescription(
    val description: String,
)
