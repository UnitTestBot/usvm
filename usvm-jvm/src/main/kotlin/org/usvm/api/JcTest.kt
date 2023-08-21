package org.usvm.api

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.cfg.JcInst

// Reflection-based API

data class JcTestSuite(
    val tests: List<JcTest>
)

data class JcTest(
    val method: JcTypedMethod,
    val before: JcParametersState?,
    val after: JcParametersState?,
    val result: Result<Any?>?,
    val coverage: JcCoverage,
)

data class JcParametersState(
    val thisInstance: Any?,
    val parameters: List<Any?>,
)

data class JcCoverage(
    val targetClassToCoverage: Map<JcClassOrInterface, JcClassCoverage>,
)

data class JcClassCoverage(
    val visitedStmts: Set<JcInst>,
)
