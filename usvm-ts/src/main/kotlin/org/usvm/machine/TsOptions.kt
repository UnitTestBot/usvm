package org.usvm.machine

data class TsOptions(
    val interproceduralAnalysis: Boolean = true,
    val enableVisualization: Boolean = false,
    val maxArraySize: Int = 1_000,
)
