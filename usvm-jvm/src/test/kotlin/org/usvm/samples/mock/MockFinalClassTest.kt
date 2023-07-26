// TODO unsupported

//package org.usvm.samples.mock
//
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner
//import org.usvm.samples.mock.others.FinalClass
//
//.OTHER_CLASSES
//import org.usvm.test.util.checkers.ge
//
//import org.usvm.testing.singleMock
//import org.usvm.testing.value
//
//internal class MockFinalClassTest : JavaMethodTestRunner(
//    testClass = MockFinalClassExample::class,
//    pipelines = listOf(
//        TestLastStage(CodegenLanguage.JAVA),
//        TestLastStage(CodegenLanguage.KOTLIN)
//    )
//) {
//    @Test
//    fun testFinalClass() {
//        checkMocks(
//            MockFinalClassExample::useFinalClass,
//            ge(2),
//            { mocks, r ->
//                val intProvider = mocks.singleMock("intProvider", FinalClass::provideInt)
//                intProvider.value<Int>(0) == 1 && r == 1
//            },
//            { mocks, r ->
//                val intProvider = mocks.singleMock("intProvider", FinalClass::provideInt)
//                intProvider.value<Int>(0) != 1 && r == 2
//            },
//            mockStrategy = OTHER_CLASSES
//        )
//    }
//}