package org.usvm.machine.utils

import org.usvm.machine.interpreters.ConcretePythonInterpreter

class PythonMachineStatistics {
    val functionStatistics = mutableListOf<PythonMachineStatisticsOnFunction>()

    private val lostSymbolicValues: Map<MethodDescription, Int>
        get() {
            val map = mutableMapOf<MethodDescription, Int>()
            functionStatistics.forEach { functionStatistics ->
                functionStatistics.lostSymbolicValues.forEach {
                    if (map[it] == null)
                        map[it] = 0
                    map[it] = map[it]!! + 1
                }
            }
            return map
        }

    fun writeReport(): String {
        val result = StringBuilder()
        result.append("Lost symbolic values:\n")
        lostSymbolicValues.toList().sortedBy { -it.second }.forEach { (description, count) ->
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
}

class PythonMachineStatisticsOnFunction {
    val lostSymbolicValues = mutableListOf<MethodDescription>()
}

data class MethodDescription(
    val description: String
)