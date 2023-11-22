package org.usvm.samples.approximations

import decoders.java.lang.Double_DecoderTests
import decoders.java.lang.Integer_DecoderTests
import decoders.java.lang.Object_DecoderTests
import decoders.java.lang.StringBuffer_DecoderTests
import decoders.java.lang.StringBuilder_DecoderTests
import decoders.java.util.ArrayList_DecoderTests
import decoders.java.util.HashSet_DecoderTests
import decoders.java.util.OptionalInt_DecoderTests
import decoders.java.util.OptionalLong_DecoderTests
import decoders.java.util.Optional_DecoderTests
import decoders.java.util.concurrent.atomic.AtomicBoolean_DecoderTests
import decoders.java.util.concurrent.atomic.AtomicInteger_DecoderTests
import decoders.java.util.concurrent.atomic.AtomicLong_DecoderTests
import decoders.java.util.concurrent.atomic.AtomicReference_DecoderTests
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Disabled
import org.usvm.logger
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.declaringClass
import kotlin.reflect.KFunction1
import kotlin.test.Test

class ApproximationsDecoderTest : ApproximationsTestRunner() {
    @Test
    fun testOptionalDecoder() {
        checkProperties(
            Optional_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && !obj.isPresent },
            { obj, result -> result == 1 && obj.isPresent && obj.get() == 123 },
            { obj, result -> result == 2 && obj.isPresent && obj.get() == "ABC" },
        )
    }

    @Test
    fun testDoubleDecoder() {
        checkProperties(
            Double_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && obj == 3.14 }
        )
    }

    @Test
    fun testIntegerDecoder() {
        checkProperties(
            Integer_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && obj == 73 }
        )
    }

    @Test
    fun testObjectDecoder() {
        checkProperties(
            Object_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && obj == null },
            { obj, result -> result == 1 && obj != null }
        )
    }

    @Test
    fun testOptionalIntegerDecoder() {
        checkProperties(
            OptionalInt_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && !obj.isPresent },
            { obj, result -> result == 1 && obj.isPresent && obj.asInt == 128 }
        )
    }

    @Test
    fun testOptionalLongDecoder() {
        checkProperties(
            OptionalLong_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && !obj.isPresent },
            { obj, result -> result == 1 && obj.isPresent && obj.asLong == 256L }
        )
    }

    @Test
    fun testAtomicBooleanDecoder() {
        checkProperties(
            AtomicBoolean_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && obj.get() },
            { obj, result -> result == 1 && !obj.get() }
        )
    }

    @Test
    fun testAtomicIntegerDecoder() {
        checkProperties(
            AtomicInteger_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && obj.get() == 123 },
        )
    }

    @Test
    fun testAtomicLongDecoder() {
        checkProperties(
            AtomicLong_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && obj.get() == 321L },
        )
    }

    @Test
    @Disabled("Incorrect decoder")
    fun testAtomicReferenceDecoder() {
        checkProperties(
            AtomicReference_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> result == 0 && obj.get() == null },
            { obj, result -> result == 1 && obj.get() == 73 },
            { obj, result -> result == 2 && obj.get() == "XYZ" },
        )
    }

    @Test
    @Disabled("Incorrect decoder")
    fun testArrayListDecoder() {
        checkProperties(
            ArrayList_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> TODO("$obj $result") }
        )
    }

    @Test
    @Disabled("Requires more decoders")
    fun testHashSetDecoder() {
        checkProperties(
            HashSet_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> TODO("$obj $result") }
        )
    }

    @Test
    @Disabled
    fun testStringBufferDecoder() {
        checkProperties(
            StringBuffer_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> TODO("$obj $result") }
        )
    }

    @Test
    @Disabled
    fun testStringBuilderDecoder() {
        checkProperties(
            StringBuilder_DecoderTests::test_0,
            ignoreNumberOfAnalysisResults,
            { obj, result -> TODO("$obj $result") }
        )
    }

    private val executedDecoderTests = mutableSetOf<Class<*>>()

    private inline fun <reified T, reified R> checkProperties(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, R?) -> Boolean,
        invariants: Array<(T, R?) -> Boolean> = emptyArray(),
    ) {
        executedDecoderTests += method.declaringClass!!
        checkDiscoveredProperties(
            method,
            analysisResultsNumberMatcher,
            *analysisResultsMatchers,
            invariants = invariants
        )
    }

    @AfterAll
    fun verifyAllDecoderTestsComplete() {
        val allDecoderTests = cp.locations
            .single {
                it.classNames?.contains(Optional_DecoderTests::class.java.name) == true
            }
            .classNames
            ?.filter { "Decoder" in it }
            ?.toSet()
            ?: emptySet()

        val executedTests = executedDecoderTests.mapTo(hashSetOf()) { it.name }
        val missedTests = allDecoderTests - executedTests
        if (missedTests.isNotEmpty()) {
            logger.warn { "Missed decoder tests: $missedTests" }
        }
    }
}
