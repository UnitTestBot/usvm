package org.usvm.samples.approximations

import decoders.java.util.Optional_DecoderTests
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.test.Test

class ApproximationsDecoderTest : ApproximationsTestRunner() {
    // Todo: rewrite tmp test
    @Test
    fun testOptionalDecoder() {
        checkDiscoveredPropertiesWithExceptions(
            Optional_DecoderTests::symbolicList,
            ignoreNumberOfAnalysisResults,
            { optional, result -> println("$optional $result"); true }
        )
    }
}
