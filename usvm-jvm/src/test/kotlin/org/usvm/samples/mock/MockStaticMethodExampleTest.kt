// TODO unsupported
//package org.usvm.samples.mock
//
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner
//
//import org.usvm.framework.plugin.api.UtPrimitiveModel
//import org.usvm.framework.plugin.api.util.id
//import org.usvm.framework.util.singleModel
//import org.usvm.framework.util.singleStaticMethod
//import org.usvm.framework.util.singleValue
//import org.usvm.test.util.checkers.eq
//import org.usvm.testing.TestExecution
//
//
//internal class MockStaticMethodExampleTest : JavaMethodTestRunner(
//    testClass = MockStaticMethodExample::class,
//    testCodeGeneration = true,
//    pipelines = listOf(
//        TestLastStage(CodegenLanguage.JAVA, lastStage = TestExecution),
//        TestLastStage(CodegenLanguage.KOTLIN, lastStage = CodeGeneration)
//    )
//) {
//    @Test
//    fun testUseStaticMethod() {
//        checkMocksAndInstrumentation(
//            MockStaticMethodExample::useStaticMethod,
//            eq(2),
//            { _, instrumentation, r ->
//                val mockValue = instrumentation
//                    .singleStaticMethod("nextRandomInt")
//                    .singleModel<UtPrimitiveModel>()
//                    .singleValue() as Int
//
//                mockValue > 50 && r == 100
//            },
//            { _, instrumentation, r ->
//                val mockValue = instrumentation
//                    .singleStaticMethod("nextRandomInt")
//                    .singleModel<UtPrimitiveModel>()
//                    .singleValue() as Int
//
//                mockValue <= 50 && r == 0
//            },
//        )
//    }
//
//    @Test
//    fun testMockStaticMethodFromAlwaysMockClass() {
//        checkMocksAndInstrumentation(
//            MockStaticMethodExample::mockStaticMethodFromAlwaysMockClass,
//            eq(1),
//            additionalMockAlwaysClasses = setOf(System::class.id)
//        )
//    }
//}