// TODO unsupported
//package org.usvm.samples.mock
//
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner.OTHER_PACKAGES
//import org.usvm.framework.plugin.api.UtModel
//import org.usvm.framework.plugin.api.isMockModel
//import org.usvm.framework.plugin.api.isNotNull
//import org.usvm.framework.plugin.api.isNull
//import org.usvm.test.util.checkers.eq
//import org.usvm.testing.UtModelTestCaseChecker
//import org.usvm.testing.getOrThrow
//import org.usvm.testing.primitiveValue
//
//internal class InnerMockWithFieldChecker : UtModelTestCaseChecker(testClass = InnerMockWithFieldExample::class) {
//    @Test
//    fun testCheckAndUpdate() {
//        checkStatic(
//            InnerMockWithFieldExample::checkAndUpdate,
//            eq(4),
//            { example, r -> example.isNull() && r.isException<NullPointerException>() },
//            { example, r -> example.isNotNull() && example.stamp.isNull() && r.isException<NullPointerException>() },
//            { example, r ->
//                val result = r.getOrThrow()
//                val isMockModels = example.stamp.isMockModel() && result.isMockModel()
//                val stampConstraint = example.stamp.initial > example.stamp.version
//                val postcondition = result.initial == example.stamp.initial && result.version == result.initial
//
//                isMockModels && stampConstraint && postcondition
//            },
//            { example, r ->
//                val result = r.getOrThrow()
//                val stamp = example.stamp
//
//                val isMockModels = stamp.isMockModel() && result.isMockModel()
//                val stampConstraint = stamp.initial <= stamp.version
//                val postcondition = result.initial == stamp.initial && result.version == stamp.version + 1
//
//                isMockModels && stampConstraint && postcondition
//            },
//            mockStrategy = OTHER_PACKAGES
//        )
//    }
//
//    private val UtModel.stamp: UtModel
//        get() = findField("stamp")
//
//    private val UtModel.initial: Int
//        get() = findField("initial").primitiveValue()
//
//    private val UtModel.version: Int
//        get() = findField("version").primitiveValue()
//}