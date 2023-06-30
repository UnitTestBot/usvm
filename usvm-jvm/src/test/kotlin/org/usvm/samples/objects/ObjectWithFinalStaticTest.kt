package org.usvm.samples.objects

// TODO unsupported
//class ObjectWithFinalStaticTest : JavaMethodTestRunner() {
//    @Test
//    fun testParameterEqualsFinalStatic() {
//        checkStatics(
//            ObjectWithFinalStatic::parameterEqualsFinalStatic,
//            eq(2),
//            { key, _, statics, result -> key != statics.singleValue() as Int  && result == -420 },
//            // matcher checks equality by value, but branch is executed if objects are equal by reference
//            { key, i, statics, result -> key == statics.singleValue() && i == result },
//        )
//    }
//}