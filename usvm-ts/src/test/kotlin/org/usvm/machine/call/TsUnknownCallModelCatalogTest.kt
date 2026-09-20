package org.usvm.machine.call

import io.mockk.mockk
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UMachineOptions
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.intrinsic.TsArrayShiftIntrinsicModel
import org.usvm.machine.call.intrinsic.TsNumericIntrinsicModelFamily
import org.usvm.machine.state.TsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TsUnknownCallModelCatalogTest {
    private val callSite = mockk<EtsStmt>()

    @Test
    fun `model IDs and target names must be non blank`() {
        assertFailsWith<IllegalArgumentException> {
            TsUnknownCallModelCatalog(listOf(model(id = " ")))
        }
        assertFailsWith<IllegalArgumentException> {
            TsUnknownCallTarget(methodName = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            TsUnknownCallTarget(methodName = "method", enclosingClassName = " ")
        }
    }

    @Test
    fun `duplicate IDs are rejected`() {
        val error = assertFailsWith<IllegalArgumentException> {
            TsUnknownCallModelCatalog(
                models = listOf(
                    model(id = "duplicate", methodName = "first"),
                    model(id = "duplicate", methodName = "second"),
                )
            )
        }

        assertEquals("Duplicate semantic model ID: duplicate", error.message)
    }

    @Test
    fun `overlapping declarative targets are rejected before execution`() {
        val error = assertFailsWith<IllegalStateException> {
            TsUnknownCallModelCatalog(
                models = listOf(
                    model(id = "z-model", methodName = "target"),
                    model(
                        id = "a-model",
                        methodName = "target",
                        failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
                    ),
                )
            )
        }

        assertEquals("Ambiguous semantic model targets: a-model, z-model", error.message)
    }

    @Test
    fun `unknown enabled IDs are rejected`() {
        val error = assertFailsWith<IllegalArgumentException> {
            TsUnknownCallModelCatalog(
                models = listOf(model(id = "known")),
                selection = TsUnknownCallModelSelection.Only(setOf("missing")),
            )
        }

        assertEquals("Unknown semantic model IDs: missing", error.message)
    }

    @Test
    fun `selection does not depend on model order`() {
        val forward = listOf(
            model(id = "a", methodName = "first"),
            model(id = "b", methodName = "second"),
        )

        val first = TsUnknownCallModelCatalog(forward)
        val second = TsUnknownCallModelCatalog(forward.reversed())

        assertEquals(listOf("a", "b"), first.modelIds)
        assertEquals(first.modelIds, second.modelIds)
    }

    @Test
    fun `enabled subset is detached from mutable selection`() {
        val mutableIds = mutableSetOf("a")
        val models = listOf(
            model(id = "a", methodName = "first"),
            model(id = "b", methodName = "second"),
        )
        val onlyA = TsUnknownCallModelCatalog(models, selection = TsUnknownCallModelSelection.Only(mutableIds))
        mutableIds += "b"

        assertEquals(listOf("a"), onlyA.modelIds)
    }

    @Test
    fun `class and reason wildcards reject exactly overlapping targets in either ID order`() {
        val reasons = listOf(null) + TsUnknownCallFailureReason.entries
        val targets = reasons.flatMap { reason ->
            listOf(null, "A", "B").map { klass ->
                TsUnknownCallTarget(methodName = "method", failureReason = reason, enclosingClassName = klass)
            }
        }
        for (left in targets) for (right in targets) {
            val reasonOverlaps = left.failureReason == null || right.failureReason == null ||
                left.failureReason == right.failureReason
            val classOverlaps = left.enclosingClassName == null || right.enclosingClassName == null ||
                left.enclosingClassName == right.enclosingClassName
            val models = listOf(FakeModel(id = "a", target = left), FakeModel(id = "b", target = right))

            if (reasonOverlaps && classOverlaps) {
                assertFailsWith<IllegalStateException> { TsUnknownCallModelCatalog(models) }
                assertFailsWith<IllegalStateException> {
                    TsUnknownCallModelCatalog(
                        listOf(FakeModel(id = "b", target = left), FakeModel(id = "a", target = right))
                    )
                }
            } else {
                assertSelections(models)
            }
        }
    }

    private fun assertSelections(models: List<TsUnknownCallModel>) {
        val catalog = TsUnknownCallModelCatalog(models)
        for (reason in TsUnknownCallFailureReason.entries) for (klass in listOf("A", "B", "C")) {
            val expected = models.singleOrNull {
                (it.target.failureReason == null || it.target.failureReason == reason) &&
                    (it.target.enclosingClassName == null || it.target.enclosingClassName == klass)
            }
            assertSame(expected, catalog.select(call(klass, reason)))
        }
    }

    @Test
    fun `built in models are discovered once and an explicit empty selection disables all`() {
        val catalog = TsBuiltInUnknownCallModels.catalog()

        val expectedModelIds = listOf(
            "ts.array.includes",
            "ts.array.indexOf",
            "ts.array.lastIndexOf",
            "ts.array.pop",
            TsArrayShiftIntrinsicModel.MODEL_ID,
            TsNumericIntrinsicModelFamily.MATH_ABS_ID,
            TsNumericIntrinsicModelFamily.MATH_CEIL_ID,
            TsNumericIntrinsicModelFamily.MATH_MAX_ID,
            TsNumericIntrinsicModelFamily.MATH_MIN_ID,
            TsNumericIntrinsicModelFamily.MATH_ROUND_ID,
            TsNumericIntrinsicModelFamily.NUMBER_IS_INTEGER_ID,
            "ts.string.charAt",
            "ts.string.charCodeAt",
            "ts.string.endsWith",
            "ts.string.includes",
            "ts.string.indexOf",
            "ts.string.lastIndexOf",
            "ts.string.primitive.codeUnitAt",
            "ts.string.primitive.fromCodeUnit",
            "ts.string.primitive.length",
            "ts.string.startsWith",
        )

        assertEquals(expectedModelIds, catalog.modelIds)
        assertSame(catalog, TsBuiltInUnknownCallModels.catalog())
        assertFailsWith<UnsupportedOperationException> { (catalog.modelIds as MutableList<String>).clear() }
        assertEquals(
            expectedModelIds,
            TsBuiltInUnknownCallModels.catalog().modelIds,
        )
        assertTrue(TsBuiltInUnknownCallModels.catalog(TsUnknownCallModelSelection.Only(emptySet())).modelIds.isEmpty())
    }

    @Test
    fun `unmatched call selects no model`() {
        val catalog = TsUnknownCallModelCatalog(listOf(model(id = "known", methodName = "known")))

        assertNull(catalog.select(call(className = "A", reason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION)))
    }

    private fun call(className: String, reason: TsUnknownCallFailureReason) = TsUnknownCall(
        callee = EtsMethodSignature(
            enclosingClass = EtsClassSignature.UNKNOWN.copy(name = className),
            name = "method",
            parameters = emptyList(),
            returnType = EtsUnknownType,
        ),
        receiver = null,
        arguments = emptyList(),
        resultType = EtsUnknownType,
        callSite = callSite,
        failureReason = reason,
    )

    @Test
    fun `same model EtsIR file object is merged once`() {
        val modelFile = etsFile(fileName = "model.ts")
        val catalog = TsUnknownCallModelCatalog(
            models = listOf(
                model(id = "a", methodName = "first", additionalSceneFiles = listOf(modelFile)),
                model(id = "b", methodName = "second", additionalSceneFiles = listOf(modelFile)),
            )
        )

        assertEquals(listOf(modelFile), catalog.additionalSceneFiles)
        assertFailsWith<UnsupportedOperationException> {
            (catalog.additionalSceneFiles as MutableList<EtsFile>).clear()
        }
    }

    @Test
    fun `distinct model EtsIR files with the same signature are rejected`() {
        val first = etsFile(fileName = "model.ts")
        val second = etsFile(fileName = "model.ts")

        val error = assertFailsWith<IllegalArgumentException> {
            TsUnknownCallModelCatalog(
                models = listOf(
                    model(id = "a", methodName = "first", additionalSceneFiles = listOf(first)),
                    model(id = "b", methodName = "second", additionalSceneFiles = listOf(second)),
                )
            )
        }

        assertEquals("Conflicting EtsIR files share signature @test/model", error.message)
    }

    @Test
    fun `application and model EtsIR files with the same signature are rejected`() {
        val applicationFile = etsFile(fileName = "shared.ts")
        val modelFile = etsFile(fileName = "shared.ts")
        val catalog = TsUnknownCallModelCatalog(
            models = listOf(
                model(id = "model", additionalSceneFiles = listOf(modelFile)),
            )
        )

        val error = assertFailsWith<IllegalArgumentException> {
            TsMachine(
                scene = EtsScene(projectFiles = listOf(applicationFile)),
                options = UMachineOptions(),
                tsOptions = TsOptions(),
                unknownCallModels = catalog,
            )
        }

        assertEquals("Conflicting EtsIR files share signature @test/shared", error.message)
    }

    @Test
    fun `SDK and model EtsIR files with the same signature are rejected`() {
        val sdkFile = etsFile(fileName = "shared.ts")
        val modelFile = etsFile(fileName = "shared.ts")
        val catalog = TsUnknownCallModelCatalog(
            models = listOf(model(id = "model", additionalSceneFiles = listOf(modelFile))),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            TsMachine(
                scene = EtsScene(projectFiles = emptyList(), sdkFiles = listOf(sdkFile)),
                options = UMachineOptions(),
                tsOptions = TsOptions(),
                unknownCallModels = catalog,
            ).close()
        }

        assertEquals("Conflicting EtsIR files share signature @test/shared", error.message)
    }

    private fun model(
        id: String,
        methodName: String = "target-$id",
        failureReason: TsUnknownCallFailureReason? = null,
        className: String? = null,
        additionalSceneFiles: List<EtsFile> = emptyList(),
    ): TsUnknownCallModel = FakeModel(
        id = id,
        target = TsUnknownCallTarget(
            methodName = methodName,
            failureReason = failureReason,
            enclosingClassName = className,
        ),
        additionalSceneFiles = additionalSceneFiles,
    )

    private class FakeModel(
        override val id: String,
        override val target: TsUnknownCallTarget,
        override val additionalSceneFiles: List<EtsFile> = emptyList(),
    ) : TsUnknownCallModel {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution =
            error("Fake model must not execute in catalog metadata tests")
    }

    private fun etsFile(fileName: String): EtsFile = EtsFile(
        signature = EtsFileSignature(projectName = "test", fileName = fileName),
        classes = emptyList(),
        namespaces = emptyList(),
    )
}
