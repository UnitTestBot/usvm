// TODO unsupported

//package org.usvm.samples.mock
//
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner
//import org.usvm.framework.plugin.api.UtCompositeModel
//import org.usvm.framework.plugin.api.UtNewInstanceInstrumentation
//import org.usvm.test.util.checkers.eq
//import org.usvm.testing.TestExecution
//
//import org.usvm.testing.isParameter
//import org.usvm.testing.mockValues
//import org.usvm.testing.mocksMethod
//import org.usvm.testing.singleMock
//import org.usvm.testing.value
//import java.util.Random
//
//internal class MockRandomTest : JavaMethodTestRunner(
//    testClass = MockRandomExamples::class,
//    testCodeGeneration = true,
//    pipelines = listOf(
//        TestLastStage(CodegenLanguage.JAVA, lastStage = TestExecution),
//        TestLastStage(CodegenLanguage.KOTLIN, lastStage = CodeGeneration)
//    )
//) {
//    @Test
//    fun testRandomAsParameter() {
//        val method: Random.() -> Int = Random::nextInt
//        checkMocks(
//            MockRandomExamples::randomAsParameter,
//            eq(3),
//            { _, random, _, _, r -> random == null && r == null }, // NPE
//            { _, random, threshold, mocks, r ->
//                val mock = mocks.single()
//                assert(mock.isParameter(1) && mock.mocksMethod(method))
//                val nextInt = mock.value<Int>()
//
//                random == null && nextInt > threshold && r == threshold + 1
//            },
//            { _, random, threshold, mocks, r ->
//                val mock = mocks.single()
//                assert(mock.isParameter(1) && mock.mocksMethod(method))
//                val nextInt = mock.value<Int>()
//
//                random == null && nextInt <= threshold && r == nextInt
//            },
//        )
//    }
//
//    @Test
//    fun testRandomAsField() {
//        val method: Random.() -> Int = Random::nextInt
//        checkMocks(
//            MockRandomExamples::randomAsField,
//            eq(3),
//            { _, _, r -> r == null }, // NPE
//            { threshold, mocks, r ->
//                val mock = mocks.singleMock("random", method)
//                val nextInt = mock.value<Int>()
//
//                nextInt > threshold && r == threshold + 1
//            },
//            { threshold, mocks, r ->
//                val mock = mocks.singleMock("random", method)
//                val nextInt = mock.value<Int>()
//
//                nextInt <= threshold && r == nextInt
//            },
//        )
//    }
//
//    @Test
//    fun testRandomAsLocalVariable() {
//        checkMocksAndInstrumentation(
//            MockRandomExamples::randomAsLocalVariable,
//            eq(2),
//            { _, instrumentation, r ->
//                val mockInstances = instrumentation
//                    .filterIsInstance<UtNewInstanceInstrumentation>()
//                    .single { it.classId.name == "java.util.Random" }
//                    .instances
//                    //TODO: support any UtModel here after SAT-1135 is completed
//                    .filterIsInstance<UtCompositeModel>()
//
//                assert(mockInstances.size == 2)
//
//                val firstMockValues = mockInstances[0].mockValues<Int>("nextInt")
//                val secondMockValues = mockInstances[1].mockValues<Int>("nextInt")
//
//                val sizes = firstMockValues.size == 2 && secondMockValues.size == 2
//                val valueConstraint = firstMockValues[0] + firstMockValues[1] + secondMockValues[0] > 1000
//                val resultConstraint = r == secondMockValues[1]
//
//                sizes && valueConstraint && resultConstraint
//            },
//            { _, instrumentation, r ->
//                val mockInstances = instrumentation
//                    .filterIsInstance<UtNewInstanceInstrumentation>()
//                    .single { it.classId.name == "java.util.Random" }
//                    .instances
//                    .filterIsInstance<UtCompositeModel>()
//
//                assert(mockInstances.size == 3)
//
//                val firstMockValues = mockInstances[0].mockValues<Int>("nextInt")
//                val secondMockValues = mockInstances[1].mockValues<Int>("nextInt")
//                val thirdMockValues = mockInstances[2].mockValues<Int>("nextInt")
//
//                val sizes = firstMockValues.size == 2 && secondMockValues.size == 1 && thirdMockValues.size == 1
//                val valueConstraint = firstMockValues[0] + firstMockValues[1] + secondMockValues[0] <= 1000
//                val resultConstraint = r == thirdMockValues[0]
//
//                sizes && valueConstraint && resultConstraint
//            },
//        )
//    }
//
//    @Test
//    fun testUseSecureRandom() {
//        checkMocksAndInstrumentation(
//            MockRandomExamples::useSecureRandom,
//            eq(2),
//            { _, instrumentation, r ->
//                val mock = instrumentation
//                    .filterIsInstance<UtNewInstanceInstrumentation>()
//                    .single { it.classId.name == "java.security.SecureRandom" }
//                    .instances
//                    .filterIsInstance<UtCompositeModel>()
//                    .single()
//
//                val values = mock.mockValues<Int>("nextInt")
//
//                values.size == 1 && values[0] > 1000 && r == 1
//            },
//            { _, instrumentation, r ->
//                val mock = instrumentation
//                    .filterIsInstance<UtNewInstanceInstrumentation>()
//                    .single { it.classId.name == "java.security.SecureRandom" }
//                    .instances
//                    .filterIsInstance<UtCompositeModel>()
//                    .single()
//
//                val values = mock.mockValues<Int>("nextInt")
//
//                values.size == 2 && values[0] <= 1000 && r == values[1]
//            },
//        )
//    }
//}