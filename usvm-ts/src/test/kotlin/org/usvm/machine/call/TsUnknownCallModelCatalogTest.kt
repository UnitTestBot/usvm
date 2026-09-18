package org.usvm.machine.call

import io.mockk.mockk
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.machine.call.intrinsic.TsArrayShiftIntrinsicModel
import org.usvm.machine.state.TsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertNotEquals
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
    fun `selection and fingerprint do not depend on model order`() {
        val forward = listOf(
            model(id = "a", methodName = "first"),
            model(id = "b", methodName = "second"),
        )

        val first = TsUnknownCallModelCatalog(forward)
        val second = TsUnknownCallModelCatalog(forward.reversed())

        assertEquals(listOf("a", "b"), first.modelIds)
        assertEquals(first.modelIds, second.modelIds)
        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `enabled subset is detached and changes fingerprint`() {
        val mutableIds = mutableSetOf("a")
        val models = listOf(
            model(id = "a", methodName = "first"),
            model(id = "b", methodName = "second"),
        )
        val onlyA = TsUnknownCallModelCatalog(models, selection = TsUnknownCallModelSelection.Only(mutableIds))
        mutableIds += "b"
        val both = TsUnknownCallModelCatalog(models)

        assertEquals(listOf("a"), onlyA.modelIds)
        assertNotEquals(onlyA.fingerprint, both.fingerprint)
        assertTrue(onlyA.fingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `class and reason wildcards reject exactly overlapping targets in either ID order`() {
        val reasons = listOf(null) + TsUnknownCallFailureReason.entries
        val classes = listOf(null, "A", "B")
        for (leftReason in reasons) for (rightReason in reasons) {
            for (leftClass in classes) for (rightClass in classes) {
                val left = model(id = "a", methodName = "method", failureReason = leftReason, className = leftClass)
                val right = model(id = "b", methodName = "method", failureReason = rightReason, className = rightClass)
                val overlaps = (leftReason == null || rightReason == null || leftReason == rightReason) &&
                    (leftClass == null || rightClass == null || leftClass == rightClass)

                if (overlaps) {
                    assertFailsWith<IllegalStateException> { TsUnknownCallModelCatalog(listOf(left, right)) }
                    assertFailsWith<IllegalStateException> {
                        TsUnknownCallModelCatalog(listOf(
                            model(id = "b", methodName = "method", failureReason = leftReason, className = leftClass),
                            model(id = "a", methodName = "method", failureReason = rightReason, className = rightClass),
                        ))
                    }
                } else {
                    val catalog = TsUnknownCallModelCatalog(listOf(left, right))
                    for (reason in TsUnknownCallFailureReason.entries) for (klass in listOf("A", "B", "C")) {
                        val expected = listOf(left, right).singleOrNull {
                            (it.target.failureReason == null || it.target.failureReason == reason) &&
                                (it.target.enclosingClassName == null || it.target.enclosingClassName == klass)
                        }
                        assertSame(expected, catalog.select(call(klass, reason)))
                    }
                }
            }
        }
    }

    @Test
    fun `built in models are discovered once and an explicit empty selection disables all`() {
        val catalog = TsBuiltInUnknownCallModels.catalog()

        assertEquals(listOf(TsArrayShiftIntrinsicModel.MODEL_ID), catalog.modelIds)
        assertSame(catalog, TsBuiltInUnknownCallModels.catalog())
        assertTrue(TsBuiltInUnknownCallModels.catalog(TsUnknownCallModelSelection.Only(emptySet())).modelIds.isEmpty())
    }

    @Test
    fun `fingerprints preserve ID boundaries and no match remains distinct from ambiguity`() {
        val left = TsUnknownCallModelCatalog(listOf(model(id = "ab"), model(id = "c")))
        val right = TsUnknownCallModelCatalog(listOf(model(id = "a"), model(id = "bc")))

        assertNotEquals(left.fingerprint, right.fingerprint)
        assertNull(left.select(call(className = "A", reason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION)))
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

    private fun model(
        id: String,
        methodName: String = "target-$id",
        failureReason: TsUnknownCallFailureReason? = null,
        className: String? = null,
    ): TsUnknownCallModel = FakeModel(
        id = id,
        target = TsUnknownCallTarget(
            methodName = methodName,
            failureReason = failureReason,
            enclosingClassName = className,
        ),
    )

    private class FakeModel(
        override val id: String,
        override val target: TsUnknownCallTarget,
    ) : TsUnknownCallModel {
        override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelExecution =
            error("Fake model must not execute in catalog metadata tests")
    }
}
