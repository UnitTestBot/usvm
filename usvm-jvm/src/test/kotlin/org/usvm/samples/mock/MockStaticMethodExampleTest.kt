// TODO unsupported
//package org.usvm.samples.mock
//
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner
//
//import org.utbot.framework.plugin.api.UtPrimitiveModel
//import org.utbot.framework.plugin.api.util.id
//import org.utbot.framework.util.singleModel
//import org.utbot.framework.util.singleStaticMethod
//import org.utbot.framework.util.singleValue
//import org.usvm.test.util.checkers.eq
//import org.utbot.testing.TestExecution
//
//
//// TODO Kotlin mocks generics https://github.com/UnitTestBot/UTBotJava/issues/88
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