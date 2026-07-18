package org.usvm.ts.pbt.external

import org.jacodb.ets.dsl.eqq
import org.jacodb.ets.dsl.const
import org.jacodb.ets.dsl.local
import org.jacodb.ets.dsl.param
import org.jacodb.ets.dsl.program
import org.jacodb.ets.dsl.toBlockCfg
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.toEtsBlockCfg
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.coverage.CoverageTracker
import org.usvm.ts.pbt.hybrid.PbtPhase
import org.usvm.ts.pbt.interpreter.VNumber
import org.usvm.ts.pbt.interpreter.VUndefined

class ExternalTestCorpusTest {
    @Test
    fun `tagged corpus round-trips values JSON cannot represent`() {
        val values = listOf(
            ExternalValue("undefined"),
            ExternalValue("null"),
            ExternalValue("number", value = "NaN"),
            ExternalValue("number", value = "Infinity"),
            ExternalValue("number", value = "-Infinity"),
            ExternalValue("number", value = "-0"),
            ExternalValue("array", elements = listOf(ExternalValue("hole"), ExternalValue("string", value = "x"))),
        )
        val corpus = ExternalTestCorpus(
            producer = "test-generator@1",
            cases = listOf(ExternalTestCase("special", "f.ts::%dflt::f/7", arguments = values)),
        )

        val decoded = ExternalTestCorpusCodec.decode(ExternalTestCorpusCodec.encode(corpus))
        assertEquals(corpus.schemaVersion, decoded.schemaVersion)
        assertEquals(corpus.producer, decoded.producer)
        assertEquals(corpus.cases, decoded.cases)
        assertTrue(decoded.rejections.isEmpty())
    }

    @Test
    fun `JSONL keeps valid cases after a malformed case`() {
        val text = """
            {"schemaVersion":1,"producer":"jsonl-test"}
            {"id":"ok-1","methodId":"f.ts::C::f/1","arguments":[{"kind":"number","value":"1"}]}
            {not-json}
            {"id":"bad","methodId":"f.ts::C::f/1"}
            {"id":"ok-2","methodId":"f.ts::C::f/1","arguments":[{"kind":"undefined"}]}
        """.trimIndent()

        val decoded = ExternalTestCorpusCodec.decode(text, "cases.jsonl")
        assertEquals(listOf("ok-1", "ok-2"), decoded.cases.map { it.id })
        assertEquals(2, decoded.rejections.size)
        assertTrue(decoded.rejections.any { it.id == "bad" })
        assertTrue(decoded.rejections.any { "not valid JSON" in it.reason })
    }

    @Test
    fun `special numbers retain their concrete identity`() {
        val nan = ExternalValueCodec.toVValue(ExternalValue("number", value = "NaN")) as VNumber
        val negativeZero = ExternalValueCodec.toVValue(ExternalValue("number", value = "-0")) as VNumber
        assertTrue(nan.value.isNaN())
        assertEquals((-0.0).toRawBits(), negativeZero.value.toRawBits())
        assertEquals("NaN", ExternalValueCodec.fromVValue(nan).value)
        assertEquals("-0", ExternalValueCodec.fromVValue(negativeZero).value)
    }

    @Test
    fun `unknown values and array holes are explicit replay rejects`() {
        assertThrows(ExternalValueConversionException::class.java) {
            ExternalValueCodec.toVValue(ExternalValue("future-bigint", value = "1"))
        }
        assertThrows(ExternalValueConversionException::class.java) {
            ExternalValueCodec.toVValue(
                ExternalValue("array", elements = listOf(ExternalValue("number", value = "1"), ExternalValue("hole")))
            )
        }
    }

    @Test
    fun `external inputs replay first and are deduplicated across producers`() {
        val method = branchingMethod()
        val methodId = stableMethodId(method)
        val negative = ConcreteInputCase(
            id = "negative-a",
            methodId = methodId,
            arguments = listOf(VNumber(0.0)),
            producer = "producer-a",
        )
        val duplicateNegative = negative.copy(id = "negative-b", producer = "producer-b")
        val positive = ConcreteInputCase(
            id = "positive",
            methodId = methodId,
            arguments = listOf(VNumber(7.0)),
            producer = "producer-b",
        )
        val coverage = CoverageTracker(listOf(method))

        val result = PbtPhase(
            scene = EtsScene(emptyList()),
            method = method,
            coverage = coverage,
            maxIterations = 100,
            inputProviders = listOf(
                ListConcreteInputProvider("producer-a", listOf(negative)),
                ListConcreteInputProvider("producer-b", listOf(duplicateNegative, positive)),
            ),
            internalGeneration = false,
            shrink = false,
        ).run()

        assertEquals(1.0, coverage.branchCoverage(), 1e-9)
        assertEquals(3, result.stats.externalImported)
        assertEquals(2, result.stats.externalExecuted)
        assertEquals(1, result.stats.externalDeduplicated)
        assertEquals(0, result.stats.generatedExecutions)
        assertEquals(2, result.stats.executions)
        assertTrue(coverage.timeline.any { it.phase == "external:producer-a" })
        assertTrue(coverage.timeline.any { it.phase == "external:producer-b" })

        val manifest = TargetManifest.fromMethods(listOf(method))
        assertEquals(methodId, manifest.methods.single().methodId)
        assertEquals(2, manifest.methods.single().branches.size)
        val manifestJson = TargetManifest.encode(manifest)
        assertFalse(manifestJson.isBlank())
        assertTrue("\"schemaVersion\"" in manifestJson)
        assertTrue("\"generator\"" in manifestJson)
    }

    @Test
    fun `ETC provider counts an unknown kind as a rejected matching case`() {
        val method = branchingMethod()
        val corpus = ExternalTestCorpus(
            producer = "future-producer",
            cases = listOf(
                ExternalTestCase(
                    id = "new-kind",
                    methodId = stableMethodId(method),
                    arguments = listOf(ExternalValue("future-bigint", value = "42")),
                )
            ),
        )

        val batch = ExternalCorpusInputProvider.fromCorpus(corpus).inputsFor(method)
        assertEquals(1, batch.imported)
        assertTrue(batch.cases.isEmpty())
        assertEquals(1, batch.rejections.size)
        assertTrue("unknown value kind" in batch.rejections.single().reason)
    }

    private fun branchingMethod(): EtsMethod {
        val program = program {
            val x = local("x")
            assign(x, param(0))
            ifStmt(eqq(x, const(7.0))) {
                ret(const(1.0))
            }
            ret(const(0.0))
        }
        return EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = EtsClassSignature(
                    name = "%dflt",
                    file = EtsFileSignature(projectName = "p", fileName = "src/f.ts"),
                ),
                name = "f",
                parameters = listOf(EtsMethodParameter(0, "x", EtsUnknownType)),
                returnType = EtsUnknownType,
            )
        ).also { method ->
            method.body.cfg = program.toBlockCfg().toEtsBlockCfg(method)
        }
    }
}
