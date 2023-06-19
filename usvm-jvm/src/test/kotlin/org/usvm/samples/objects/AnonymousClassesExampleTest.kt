package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException


class AnonymousClassesExampleTest : JavaMethodTestRunner() {
    @Test
    fun testAnonymousClassAsParam() {
        checkWithExceptionExecutionMatches(
            AnonymousClassesExample::anonymousClassAsParam,
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
        checkExecutionMatches(
            AnonymousClassesExample::anonymousClassAsStatic,
            { _, r -> r == 42 }
        )
    }

    @Test
    fun testAnonymousClassAsResult() {
        checkExecutionMatches(
            AnonymousClassesExample::anonymousClassAsResult,
            { _, abstractAnonymousClass -> abstractAnonymousClass != null && abstractAnonymousClass::class.java.isAnonymousClass }
        )
    }
}