package org.usvm.samples.types

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class PathDependentGenericsExampleTest : JavaMethodTestRunner() {
    @Test
    fun testPathDependentGenerics() {
        checkExecutionMatches(
            PathDependentGenericsExample::pathDependentGenerics,
            eq(3),
            { _, elem, r -> elem is ClassWithOneGeneric<*> && r == 1 },
            { _, elem, r -> elem is ClassWithTwoGenerics<*, *> && r == 2 },
            { _, elem, r -> elem !is ClassWithOneGeneric<*> && elem !is ClassWithTwoGenerics<*, *> && r == 3 },
        )
    }

    @Test
    fun testFunctionWithSeveralTypeConstraintsForTheSameObject() {
        checkExecutionMatches(
            PathDependentGenericsExample::functionWithSeveralTypeConstraintsForTheSameObject,
            eq(2),
            { _, e, r -> e !is List<*> && r == 3 },
            { _, e, r -> e is List<*> && r == 1 },
        )
    }
}