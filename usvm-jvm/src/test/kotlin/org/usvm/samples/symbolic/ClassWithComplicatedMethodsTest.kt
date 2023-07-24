// TODO unsupported

//package org.usvm.samples.make.symbolic
//
//import org.junit.jupiter.api.Disabled
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner
//
//import org.usvm.test.util.checkers.eq
//import org.usvm.test.util.checkers.withoutConcrete
//import org.usvm.testing.Compilation
//
//import kotlin.math.abs
//import kotlin.math.sqrt
//
//// This class is substituted with ComplicatedMethodsSubstitutionsStorage
//// but we cannot do in code generation.
//// For this reason code generation executions are disabled
//internal class ClassWithComplicatedMethodsTest : JavaMethodTestRunner(
//    testClass = ClassWithComplicatedMethods::class,
//    testCodeGeneration = true,
//    pipelines = listOf(
//        TestLastStage(CodegenLanguage.JAVA, Compilation),
//        TestLastStage(CodegenLanguage.KOTLIN, Compilation)
//    )
//) {
//    @Test
//    @Disabled("[SAT-1419]")
//    fun testApplyMethodWithSideEffectAndReturn() {
//        checkMocksAndInstrumentation(
//            ClassWithComplicatedMethods::applyMethodWithSideEffectAndReturn,
//            eq(2),
//            { _, x, mocks, instr, r ->
//                x > 0 && mocks.isEmpty() && instr.isEmpty() && sqrt(x.toDouble()) == x.toDouble() && r != null && r.a == 2821
//            },
//            { _, x, mocks, instr, r ->
//                x > 0 && mocks.isEmpty() && instr.isEmpty() && sqrt(x.toDouble()) != x.toDouble() && r != null && r.a == 10
//            },
//        )
//    }
//
//    @Test
//    fun testCreateWithOriginalConstructor() {
//        checkMocksAndInstrumentation(
//            ClassWithComplicatedMethods::createWithOriginalConstructor,
//            eq(1),
//            { _, a, b, mocks, instr, r -> a > 10 && b > 10 && r != null && r.a == a + b && mocks.isEmpty() && instr.isEmpty() },
//        )
//    }
//
//    @Test
//    fun testCreateWithSubstitutedConstructor() {
//        withoutConcrete { // TODO: concrete execution can't handle this
//            checkMocksAndInstrumentation(
//                ClassWithComplicatedMethods::createWithSubstitutedConstructor,
//                eq(1),
//                { _, a, b, mocks, instr, r -> a < 0 && b < 0 && r != null && r.a == (a + b).toInt() && mocks.isEmpty() && instr.isEmpty() },
//            )
//        }
//    }
//
//    @Test
//    fun testSqrt2() {
//        checkMocksAndInstrumentation(
//            ClassWithComplicatedMethods::sqrt2,
//            eq(1),
//            { mocks, instr, r -> abs(r != null && r - sqrt(2.0)) < eps && mocks.isEmpty() && instr.isEmpty() },
//        )
//    }
//
//    @Test
//    fun testReturnSubstitutedMethod() {
//        withoutConcrete { // TODO: concrete execution can't handle this
//            checkMocksAndInstrumentation(
//                ClassWithComplicatedMethods::returnSubstitutedMethod,
//                eq(1),
//                { _, x, mocks, instr, r -> x > 100 && mocks.isEmpty() && instr.isEmpty() && r != null && r.a == x },
//            )
//        }
//    }
//
//    @Test
//    fun testAssumesWithMocks() {
//        checkMocksAndInstrumentation(
//            ClassWithComplicatedMethods::assumesWithMocks,
//            eq(1),
//            { _, x, mocks, instr, r -> x in 6..7 && r == 1 && mocks.isEmpty() && instr.isEmpty() },
//            mockStrategy = MockStrategyApi.OTHER_CLASSES
//        )
//    }
//
//    private val eps = 1e-8
//}