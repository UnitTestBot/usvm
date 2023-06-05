package org.usvm.samples.mock.fields

import org.usvm.samples.JavaMethodTestRunner


class ClassUsingClassWithRandomFieldTest : JavaMethodTestRunner() {
    // TODO unsupported
//    @Test
//    fun testUseClassWithRandomField() {
//        checkMocksAndInstrumentation(
//            ClassUsingClassWithRandomField::useClassWithRandomField,
//            eq(1),
//            { mocks, instrumentation, r ->
//                val noMocks = mocks.isEmpty()
//
//                val constructorMock = instrumentation.single() as UtNewInstanceInstrumentation
//                val classIdEquality = constructorMock.classId == java.util.Random::class.id
//                val callSiteIdEquality = constructorMock.callSites.single() == ClassWithRandomField::class.id
//                val instance = constructorMock.instances.single() as UtCompositeModel
//                val methodMock = instance.mocks.entries.single()
//                val methodNameEquality = methodMock.key.name == "nextInt"
//                val mockValueResult = r == (methodMock.value.single() as UtPrimitiveModel).value as Int
//
//                noMocks && classIdEquality && callSiteIdEquality && instance.isMock && methodNameEquality && mockValueResult
//            }
//        )
//    }
}
