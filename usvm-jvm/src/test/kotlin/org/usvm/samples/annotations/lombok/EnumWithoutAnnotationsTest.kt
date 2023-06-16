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
//
//internal class EnumWithoutAnnotationsTest : JavaMethodTestRunner() {
//    @Test
//    fun testGetterWithoutAnnotations() {
//        checkExecutionMatches(
//            EnumWithoutAnnotations::getConstant,
//            eq(1),
//            { _, r -> r == "Constant_1" },
//        )
//    }
//}