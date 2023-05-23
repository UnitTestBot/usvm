package org.usvm

import org.jacodb.api.cfg.JcInst
import kotlin.reflect.KClass

// Reflection-based API

data class JcParametersState(
    val thisInstance: Any?,
    val parameters: List<Any?>,
)

data class JcClassCoverage(
    val visitedStmts: Set<JcInst>,
)

data class JcCoverage(
    val targetClassToCoverage: Map<KClass<*>, JcClassCoverage>,
)

data class JcTest(
    val before: JcParametersState,
    val after: JcParametersState,
    val result: Result<Any?>,
    val coverage: JcCoverage,
)

data class JcTestSuite(
    val tests: List<JcTest>
)
