package org.usvm.samples.codegen.deepequals

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

import org.usvm.test.util.checkers.eq


class ClassWithCrossReferenceRelationshipTest : JavaMethodTestRunner() {
    @Test
    fun testClassWithCrossReferenceRelationship() {
        checkDiscoveredProperties(
            ClassWithCrossReferenceRelationship::returnFirstClass,
            eq(2),
        )
    }
}