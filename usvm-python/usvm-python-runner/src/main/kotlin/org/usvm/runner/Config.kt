package org.usvm.runner

data class USVMPythonConfig(
    val distributionLayout: DistributionLayout,
    val javaCmd: String,
    val mypyBuildDir: String,
    val roots: Set<String>
)

sealed class USVMPythonCallableConfig

data class USVMPythonFunctionConfig(
    val module: String,
    val name: String
): USVMPythonCallableConfig()

data class USVMPythonRunConfig(
    val callableConfig: USVMPythonCallableConfig,
    val timeoutMs: Long,
    val timeoutPerRunMs: Long
)