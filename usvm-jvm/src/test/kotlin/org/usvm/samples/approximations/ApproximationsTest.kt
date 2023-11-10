package org.usvm.samples.approximations

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.annotation
import org.jacodb.api.ext.objectClass
import org.jacodb.impl.features.hierarchyExt
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
        System.err.println("-".repeat(50))
        System.err.println("Start: $test")

        val properties = Array(test.executions) { idx -> { o: Int, _: Result<Int> -> o == idx } }
        checkDiscoveredPropertiesWithExceptions(
            test.testMethod(),
            ignoreNumberOfAnalysisResults,
            *properties,
            invariants = arrayOf({ execution, r ->
                execution !in 0 until test.executions || r.getOrThrow() == execution
            })
        )
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
            .map { (method, annotation) ->
                val maxExecutions = annotation.values["executionMax"] as? Int ?: 0
                ApproximationTestCase(method, maxExecutions + 1)
            }
            .toList()
    }
}
