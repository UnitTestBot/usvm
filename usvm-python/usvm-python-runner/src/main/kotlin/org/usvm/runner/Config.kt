package org.usvm.runner

import org.usvm.python.ps.PyPathSelectorType
import org.usvm.runner.venv.VenvConfig

data class USVMPythonConfig(
    val distributionLayout: DistributionLayout,
    val javaCmd: String,
    val mypyBuildDir: String,
    val roots: Set<String>,
    val venvConfig: VenvConfig?,
    val pathSelector: PyPathSelectorType,
)

sealed class USVMPythonCallableConfig

data class USVMPythonFunctionConfig(
    val module: String,
    val name: String,
) : USVMPythonCallableConfig()

data class USVMPythonMethodConfig(
    val module: String,
    val name: String,
    val cls: String,
) : USVMPythonCallableConfig()

data class USVMPythonRunConfig(
    val callableConfig: USVMPythonCallableConfig,
    val timeoutMs: Long,
    val timeoutPerRunMs: Long,
)
