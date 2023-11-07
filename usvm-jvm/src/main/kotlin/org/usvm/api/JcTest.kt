package org.usvm.api

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.api.util.prettify

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
) {
    override fun toString(): String =
        // TODO use fully qualified name of the `method` instead of `toString`
        "JcTest(method=$method, before=$before, after=$after, result=${result.prettify()}, coverage=$coverage)"
}

data class JcParametersState(
    val thisInstance: Any?,
    val parameters: List<Any?>,
) {
    override fun toString(): String = "(this=${thisInstance.prettify()}, params=${parameters.prettify()})"
}

data class JcCoverage(
    val targetClassToCoverage: Map<JcClassOrInterface, JcClassCoverage>,
) {
    override fun toString(): String = "(${targetClassToCoverage.prettify()})"
}

data class JcClassCoverage(
    val visitedStmts: Set<JcInst>,
) {
    override fun toString(): String = "(${visitedStmts.prettify()})"
}
