//package org.usvm.samples.annotations.lombok
//
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner
//import org.usvm.test.util.checkers.eq
//import org.usvm.samples.JavaMethodTestRunner
//
//import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
//import org.usvm.util.isException
//import org.usvm.test.util.checkers.eq
//
///**
// * Tests for Lombok annotations
// *
// * We do not calculate coverage here as Lombok always make it pure
// * (see, i.e. https://stackoverflow.com/questions/44584487/improve-lombok-data-code-coverage)
// * and Lombok code is considered to be already tested itself.
// */
//internal class EnumWithAnnotationsTest : JavaMethodTestRunner() {
//    @Test
//    fun testGetterWithAnnotations() {
//        checkExecutionMatches(
//            EnumWithAnnotations::getConstant,
//            eq(1),
//            { _, r -> r == "Constant_1" },
//        )
//    }
//}