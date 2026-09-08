package org.usvm.machine.call

import org.usvm.machine.state.TsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TsUnknownCallModelCatalogTest {
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

        assertEquals("Duplicate semantic model IDs: duplicate", error.message)
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
                enabledModelIds = setOf("missing"),
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
        val onlyA = TsUnknownCallModelCatalog(models, enabledModelIds = mutableIds)
        mutableIds += "b"
        val both = TsUnknownCallModelCatalog(models)

        assertEquals(listOf("a"), onlyA.modelIds)
        assertNotEquals(onlyA.fingerprint, both.fingerprint)
        assertTrue(onlyA.fingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    private fun model(
        id: String,
        methodName: String = "target-$id",
        failureReason: TsUnknownCallFailureReason? = null,
    ): TsUnknownCallModel = FakeModel(
        id = id,
        target = TsUnknownCallTarget(
            methodName = methodName,
            failureReason = failureReason,
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
