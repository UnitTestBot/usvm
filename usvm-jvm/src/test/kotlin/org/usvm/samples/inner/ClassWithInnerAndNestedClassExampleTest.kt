package org.usvm.samples.inner

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

class ClassWithInnerAndNestedClassExampleTest : JavaMethodTestRunner() {
    @Test
    fun testAccessOuterClassField() {
        checkDiscoveredPropertiesWithExceptions(
            ClassWithInnerAndNestedClassExample.InnerClassExample::accessOuterClassField,
            eq(1),
            { _, r -> r.isSuccess }
        )
    }

    @Test
    fun testInnerClassConstructor() {
        checkDiscoveredPropertiesWithExceptions(
            ClassWithInnerAndNestedClassExample::InnerClassExample,
            eq(1),
            { _, r -> r.isSuccess }
        )
    }

    @Test
    fun testAccessOuterClassFieldWithParameter() {
        checkDiscoveredPropertiesWithExceptions(
            ClassWithInnerAndNestedClassExample.NestedClassExample::accessOuterClassFieldWithParameter,
            eq(2),
            { _, c, r -> c == null && r.isException<NullPointerException>() },
            { _, _, r -> r.isSuccess },
        )
    }

    @Test
    fun testStaticAccessOuterClassFieldWithParameter() {
        checkDiscoveredPropertiesWithExceptions(
            ClassWithInnerAndNestedClassExample.NestedClassExample::staticAccessOuterClassFieldWithParameter,
            eq(2),
            { c, r -> c == null && r.isException<NullPointerException>() },
            { _, r -> r.isSuccess },
        )
    }

    @Test
    fun testCreateInnerClassOutside() {
        checkDiscoveredProperties(
            ClassWithInnerAndNestedClassExample.NestedClassExample::createInnerClassOutside,
            eq(1),
            { _, r -> r == 0 },
        )
    }

    @Test
    fun testUseInnerClassAsParameter() {
        checkDiscoveredPropertiesWithExceptions(
            ClassWithInnerAndNestedClassExample.NestedClassExample::useInnerClassAsParameter,
            eq(2),
            { _, p, r -> p == null && r.isException<NullPointerException>() },
            { _, p, r -> p != null && r.isSuccess },
        )
    }
}
