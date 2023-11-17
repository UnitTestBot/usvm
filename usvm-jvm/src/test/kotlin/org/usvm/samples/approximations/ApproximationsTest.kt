package org.usvm.samples.approximations

import approximations.java.lang.StringBuffer_Tests
import approximations.java.util.HashSet_Tests
import approximations.java.util.zip.CRC32_Tests
import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.annotation
import org.jacodb.api.ext.objectClass
import org.jacodb.impl.features.hierarchyExt
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.usvm.logger
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.reflect.KFunction1
import kotlin.reflect.full.declaredFunctions

class ApproximationsTest : ApproximationsTestRunner() {
    init {
        options = options.copy(stepsFromLastCovered = null)
    }

    @ParameterizedTest
    @MethodSource("approximationTests")
    fun testApproximations(test: ApproximationTestCase) {
        logger.info { "-".repeat(50) }
        logger.info { "Start: $test" }

        val properties = Array(test.executions) { idx -> { o: Int, _: Result<Int> -> o == idx } }
        try {
        checkDiscoveredPropertiesWithExceptions(
                test.testMethod(),
            ignoreNumberOfAnalysisResults,
                *properties,
                invariants = arrayOf({ execution, r ->
                    execution !in 0 until test.executions || r.getOrThrow() == execution
                })
            )
            logger.info { "Success: $test" }
        } catch (ex: Throwable) {
            logger.error(ex) { "Fail: $test" }
            throw ex
        }
    }

    class ApproximationTestCase(val method: JcMethod, val executions: Int) : Arguments {
        override fun get(): Array<Any> = arrayOf(this)

        override fun toString(): String = "${method.enclosingClass.name}#${method.name}"

        @Suppress("UNCHECKED_CAST")
        fun testMethod(): KFunction1<Int, Int> =
            Class.forName(method.enclosingClass.name)
                .kotlin
                .declaredFunctions
                .single { it.name == method.name } as KFunction1<Int, Int>
    }

    private fun approximationTests(): List<ApproximationTestCase> {
        val allClasses = runBlocking {
            cp.hierarchyExt().findSubClasses(cp.objectClass, allHierarchy = true, includeOwn = true)
        }
        return allClasses
            .filter { cls ->
                cls.annotation(approximations.Test::class.java.name) != null
            }
            .sortedBy { it.name }
            .flatMap { cls -> cls.declaredMethods.sortedBy { it.name } }
            .mapNotNull { method -> method.annotation(approximations.Test::class.java.name)?.let { method to it } }
            .filterNot { (_, annotation) -> annotation.values["disabled"] == true }

            // todo: enable tests
            .filterNot { (method, _) -> method.enclosingClass.name == StringBuffer_Tests::class.java.name }
            .filterNot { (method, _) -> method.enclosingClass.name == CRC32_Tests::class.java.name }
            .filterNot { (method, _) -> method.enclosingClass.name == HashSet_Tests::class.java.name }

            .map { (method, annotation) ->
                val maxExecutions = annotation.values["executionMax"] as? Int ?: 0
                ApproximationTestCase(method, maxExecutions + 1)
            }
            .toList()
    }
}
