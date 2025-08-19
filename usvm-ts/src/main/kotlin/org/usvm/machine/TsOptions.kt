package org.usvm.machine

data class TsOptions(
    val interproceduralAnalysis: Boolean = true,
    val enableVisualization: Boolean = false,
    val checkFieldPresents: Boolean = false,
    val assumeFieldsArePresent: Boolean = false,
)
