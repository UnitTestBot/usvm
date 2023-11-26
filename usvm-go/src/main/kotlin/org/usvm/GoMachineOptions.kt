package org.usvm

data class GoMachineOptions(
    val failOnNotFullCoverage: Boolean,
    val uncoveredMethods: List<String>
)