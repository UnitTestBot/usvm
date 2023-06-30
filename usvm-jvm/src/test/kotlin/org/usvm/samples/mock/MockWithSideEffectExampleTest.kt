// TODO unsupported
//package org.usvm.samples.mock
//
//import org.junit.Test
//
//import org.usvm.test.util.checkers.eq
//
//internal class MockWithSideEffectExampleTest : JavaMethodTestRunner(testClass = MockWithSideEffectExample::class) {
//    @Test
//    fun testSideEffect() {
//        checkWithExceptionExecutionMatches(
//            MockWithSideEffectExample::checkSideEffect,
//            eq(3),
//            { _, r -> r.isException<NullPointerException>() },
//            { _, r -> r.getOrNull() == false },
//            { _, r -> r.getOrNull() == true },
//        )
//    }
//
//    @Test
//    fun testSideEffectWithoutMocks() {
//        checkWithExceptionExecutionMatches(
//            MockWithSideEffectExample::checkSideEffect,
//            eq(2),
//            { _, r -> r.isException<NullPointerException>() },
//            { _, r -> r.getOrNull() == true },
//        )
//    }
//
//    @Test
//    fun testSideEffectElimination() {
//        checkWithExceptionExecutionMatches(
//            MockWithSideEffectExample::checkSideEffectElimination,
//            eq(1),
//            { _, r -> r.getOrNull() == true },
//        )
//    }
//
//    @Test
//    fun testStaticMethodSideEffectElimination() {
//        checkWithExceptionExecutionMatches(
//            MockWithSideEffectExample::checkStaticMethodSideEffectElimination,
//            eq(1),
//            { _, r -> r.getOrNull() == true },
//        )
//    }
//
//    @Test
//    fun testStaticMethodSideEffectEliminationWithoutMocks() {
//        checkWithExceptionExecutionMatches(
//            MockWithSideEffectExample::checkStaticMethodSideEffectElimination,
//            eq(1),
//            { _, r -> r.getOrNull() == false },
//        )
//    }
//
//}