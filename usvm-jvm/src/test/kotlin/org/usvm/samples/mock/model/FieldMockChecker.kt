//package org.usvm.samples.mock.model
//
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner
//import org.usvm.samples.mock.provider.impl.ProviderImpl
//import org.usvm.samples.mock.service.impl.ServiceWithField
//import org.usvm.samples.mock.service.impl.ServiceWithField
//.OTHER_PACKAGES
//import org.usvm.framework.plugin.api.UtModel
//import org.usvm.framework.plugin.api.isNotNull
//import org.usvm.framework.plugin.api.isNull
//import org.usvm.test.util.checkers.eq
//import org.usvm.testing.UtModelTestCaseChecker
//import org.usvm.testing.primitiveValue
//
//internal class FieldMockChecker : UtModelTestCaseChecker() {
//    @Test
//    fun testMockForField_IntPrimitive() {
//        checkStatic(
//            ServiceWithField::staticCalculateBasedOnInteger,
//            eq(4),
//            { service, r -> service.isNull() && r.isException<NullPointerException>() },
//            { service, r -> service.provider.isNull() && r.isException<NullPointerException>() },
//            { service, r ->
//                service.provider.isNotNull() &&
//                        service.provider.mocksMethod(ProviderImpl::provideInteger)!!.single()
//                            .primitiveValue<Int>() > 5 && r.primitiveValue<Int>() == 1
//            },
//            { service, r ->
//                service.provider.isNotNull() &&
//                        service.provider.mocksMethod(ProviderImpl::provideInteger)!!.single()
//                            .primitiveValue<Int>() <= 5 && r.primitiveValue<Int>() == 0
//            },
//            mockStrategy = OTHER_PACKAGES
//        )
//    }
//
//    private val UtModel.provider: UtModel
//        get() = this.findField("provider")
//}