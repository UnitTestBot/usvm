// TODO unsupported

//package org.usvm.samples.mock
//
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner.OTHER_PACKAGES
//import org.usvm.framework.plugin.api.UtModel
//import org.usvm.framework.plugin.api.isMockModel
//import org.usvm.framework.plugin.api.isNull
//import org.usvm.test.util.checkers.eq
//import org.usvm.testing.UtModelTestCaseChecker
//import org.usvm.testing.getOrThrow
//import org.usvm.testing.primitiveValue
//
//internal class MockWithFieldChecker : UtModelTestCaseChecker(testClass = MockWithFieldExample::class) {
//    @Test
//    fun testCheckAndUpdate() {
//        checkExecutionMatches(
//            MockWithFieldExample::checkAndUpdate,
//            eq(3),
//            { stamp, r -> stamp.isNull() && r.isException<NullPointerException>() },
//            { stamp, r ->
//                val result = r.getOrThrow()
//
//                val mockModels = stamp.isMockModel() && result.isMockModel()
//                val stampValues = stamp.initial > stamp.version
//                val resultConstraint = result.initial == stamp.initial && result.version == result.initial
//
//                mockModels && stampValues && resultConstraint
//            },
//            { stamp, r ->
//                val result = r.getOrThrow()
//
//                val mockModels = stamp.isMockModel() && result.isMockModel()
//                val stampValues = stamp.initial <= stamp.version
//                val resultConstraint = result.initial == stamp.initial && result.version == stamp.version + 1
//
//                mockModels && stampValues && resultConstraint
//            },
//            mockStrategy = OTHER_PACKAGES
//        )
//    }
//
//    private val UtModel.initial: Int
//        get() = findField("initial").primitiveValue()
//
//    private val UtModel.version: Int
//        get() = findField("version").primitiveValue()
//}