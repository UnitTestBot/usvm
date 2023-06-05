package org.usvm.samples.mixed

import org.usvm.samples.JavaMethodTestRunner


internal class LoggerExampleTest : JavaMethodTestRunner() {
// TODO unsupported matchers

//    @Test
//    fun testExample() {
//        checkMocksAndInstrumentation(
//            LoggerExample::example,
//            eq(2),
//            { _, instrumentation, _ -> theOnlyStaticMockValue(instrumentation).isNull() },
//            { mocks, instrumentation, r -> mocks.size == 3 && instrumentation.size == 1 && r == 15 },
//            additionalDependencies = arrayOf(org.slf4j.Logger::class.java),
//        )
//    }
//
//    @Test
//    fun testLoggerUsage() {
//        checkMocksAndInstrumentation(
//            LoggerExample::loggerUsage,
//            eq(3),
//            { _, instrumentation, _ -> theOnlyStaticMockValue(instrumentation).isNull() },
//            { mocks, instrumentation, r ->
//                (mocks.single().values.single() as UtConcreteValue<*>).value == false && instrumentation.size == 1 && r == 2
//            },
//            { mocks, instrumentation, r ->
//                (mocks.single().values.single() as UtConcreteValue<*>).value == true && instrumentation.size == 1 && r == 1
//            },
//            additionalDependencies = arrayOf(org.slf4j.Logger::class.java),
//        )
//    }
//
//    private fun theOnlyStaticMockValue(instrumentation: List<UtInstrumentation>): UtModel =
//        instrumentation
//            .filterIsInstance<UtStaticMethodInstrumentation>()
//            .single()
//            .values
//            .single()
}