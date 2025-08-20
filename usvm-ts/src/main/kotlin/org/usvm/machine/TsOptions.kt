package org.usvm.machine

data class TsOptions(
    val interproceduralAnalysis: Boolean = true,
    val enableVisualization: Boolean = false,
    val checkFieldPresents: Boolean = true,
    val assumeFieldsArePresent: Boolean = true,
)
