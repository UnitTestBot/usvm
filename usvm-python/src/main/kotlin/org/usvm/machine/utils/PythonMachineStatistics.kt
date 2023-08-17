package org.usvm.machine.utils

class PythonMachineStatistics {
    val lostSymbolicValues = mutableListOf<MethodDescription>()
}

data class MethodDescription(
    val description: String
)