package org.usvm.api

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.cfg.JcInst

// Reflection-based API

data class JcTestSuite(
    val tests: List<JcTest>
)

data class JcTest(
    val method: JcTypedMethod,
    val before: JcParametersState,
    val after: JcParametersState,
    val result: Result<Any?>,
    val coverage: JcCoverage,
)

data class JcParametersState(
    val thisInstance: Any?,
    val parameters: List<Any?>,
    val statics: Map<JcClassOrInterface, List<StaticFieldValue>>
)

data class StaticFieldValue(val field: JcField, val value: Any?)

data class JcCoverage(
    val targetClassToCoverage: Map<JcClassOrInterface, JcClassCoverage>,
)

data class JcClassCoverage(
    val visitedStmts: Set<JcInst>,
)
