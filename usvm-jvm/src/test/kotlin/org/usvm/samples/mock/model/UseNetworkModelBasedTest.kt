//package org.usvm.samples.mock.model
//
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner
//import org.usvm.samples.mock.UseNetwork
//
//import org.utbot.framework.plugin.api.UtCompositeModel
//import org.utbot.framework.plugin.api.UtVoidModel
//import org.usvm.test.util.checkers.eq
//import org.utbot.testing.UtModelTestCaseChecker
//
//internal class UseNetworkModelBasedTest : UtModelTestCaseChecker(testClass = UseNetwork::class) {
//    @Test
//    fun testMockVoidMethod() {
//        checkExecutionMatches(
//            UseNetwork::mockVoidMethod,
//            eq(1),
//            { network, _ ->
//                require(network is UtCompositeModel)
//
//                val mock = network.mocks.values.single().single()
//
//                mock is UtVoidModel
//            }
//        )
//    }
//}