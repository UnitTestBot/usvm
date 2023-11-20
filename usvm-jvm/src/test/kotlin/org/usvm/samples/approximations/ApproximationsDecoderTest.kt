package org.usvm.samples.approximations

import decoders.java.lang.Double_DecoderTests
import decoders.java.lang.Integer_DecoderTests
import decoders.java.lang.Object_DecoderTests
import decoders.java.lang.StringBuffer_DecoderTests
import decoders.java.lang.StringBuilder_DecoderTests
import decoders.java.util.HashSet_DecoderTests
import decoders.java.util.Optional_DecoderTests
import org.junit.jupiter.api.Disabled
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.test.Test

class ApproximationsDecoderTest : ApproximationsTestRunner() {
    @Test
    fun testOptionalDecoder() {
        checkDiscoveredProperties(
            Optional_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && !obj.isPresent },
            { obj, result -> result == 1 && obj.isPresent && obj.get() != 123 }
        )
    }

    @Test
    fun testDoubleDecoder() {
        checkDiscoveredProperties(
            Double_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && obj == 3.14 }
        )
    }

    @Test
    fun testIntegerDecoder() {
        checkDiscoveredProperties(
            Integer_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && obj == 73 }
        )
    }

    @Test
    fun testObjectDecoder() {
        checkDiscoveredProperties(
            Object_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && obj == null },
            { obj, result -> result == 1 && obj != null }
        )
    }

    @Test
    @Disabled("Requires more decoders")
    fun testHashSetDecoder() {
        checkDiscoveredPropertiesWithExceptions(
            HashSet_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> TODO("$obj $result") }
        )
    }

    @Test
    @Disabled
    fun testStringBufferDecoder() {
        checkDiscoveredPropertiesWithExceptions(
            StringBuffer_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> TODO("$obj $result") }
        )
    }

    @Test
    @Disabled
    fun testStringBuilderDecoder() {
        checkDiscoveredPropertiesWithExceptions(
            StringBuilder_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> TODO("$obj $result") }
        )
    }
}
