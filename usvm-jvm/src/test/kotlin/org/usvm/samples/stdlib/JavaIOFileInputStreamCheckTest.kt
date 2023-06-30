package org.usvm.samples.stdlib

import org.usvm.samples.JavaMethodTestRunner


internal class JavaIOFileInputStreamCheckTest : JavaMethodTestRunner() {
    // TODO unsupported matchers
//    @Test
//    fun testRead() {
//            checkMocksAndInstrumentation(
//                JavaIOFileInputStreamCheck::read,
//                eq(1),
//                { _, _, instrumentation, r ->
//                    val constructorMock = instrumentation.single() as UtNewInstanceInstrumentation
//
//                    val classIdEquality = constructorMock.classId == java.io.FileInputStream::class.id
//                    val callSiteIdEquality = constructorMock.callSites.single() == JavaIOFileInputStreamCheck::class.id
//                    val instance = constructorMock.instances.single() as UtCompositeModel
//                    val methodMock = instance.mocks.entries.single()
//                    val methodNameEquality = methodMock.key.name == "read"
//                    val mockValueResult = r == (methodMock.value.single() as UtPrimitiveModel).value as Int
//
//                    classIdEquality && callSiteIdEquality && instance.isMock && methodNameEquality && mockValueResult
//                },
//                additionalMockAlwaysClasses = setOf(java.io.FileInputStream::class.id), // there is a problem with coverage calculation of mocked values
//            )
//    }
}