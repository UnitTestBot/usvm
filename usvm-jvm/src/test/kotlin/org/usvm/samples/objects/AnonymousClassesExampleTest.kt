package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException


class AnonymousClassesExampleTest : JavaMethodTestRunner() {
    @Test
    fun testAnonymousClassAsParam() {
        checkDiscoveredPropertiesWithExceptions(
            AnonymousClassesExample::anonymousClassAsParam,
            eq(3),
            { _, abstractAnonymousClass, r -> abstractAnonymousClass == null && r.isException<NullPointerException>() },
            { _, abstractAnonymousClass, r -> abstractAnonymousClass != null && r.getOrNull() == 0 },
            { _, abstractAnonymousClass, r -> abstractAnonymousClass != null && abstractAnonymousClass::class.java.isAnonymousClass && r.getOrNull() == 42 }
        )
    }

    // TODO unsupported
//    @Test
//    fun testNonFinalAnonymousStatic() {
//        checkStaticsAndException(
//            AnonymousClassesExample::nonFinalAnonymousStatic,
//            eq(3),
//            { statics, r -> statics.values.single().value == null && r.isException<NullPointerException>() },
//            { _, r -> r.getOrNull() == 0 },
//            { _, r -> r.getOrNull() == 42 }
//        )
//    }

    @Test
    fun testAnonymousClassAsStatic() {
        checkDiscoveredProperties(
            AnonymousClassesExample::anonymousClassAsStatic,
            eq(1),
            { _, r -> r == 42 }
        )
    }

    @Test
    fun testAnonymousClassAsResult() {
        checkDiscoveredProperties(
            AnonymousClassesExample::anonymousClassAsResult,
            eq(1),
            { _, abstractAnonymousClass -> abstractAnonymousClass != null && abstractAnonymousClass::class.java.isAnonymousClass }
        )
    }
}