package org.usvm.ts.pbt.backend

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectionCapabilityTest {
    @Test
    fun `least capable nested projection wins and diagnostics are deterministic`() {
        val capability = aggregateProjectionCapabilities(
            backendId = FAST_CHECK_ID,
            backendVersion = FAST_CHECK_VERSION,
            capabilities = listOf(
                exact(),
                approximate("inputs[1].domain", "domain.string.approximate"),
                approximate("inputs[0].domain", "domain.number.approximate"),
            ),
        )

        assertEquals(FAST_CHECK_ID, capability.backendId)
        assertEquals(FAST_CHECK_VERSION, capability.backendVersion)
        assertEquals(ProjectionLevel.APPROXIMATE, capability.level)
        assertEquals(
            listOf("domain.number.approximate", "domain.string.approximate"),
            capability.diagnostics.map { it.code },
        )
    }

    @Test
    fun `unsupported nested projection wins over approximate projection`() {
        val capability = aggregateProjectionCapabilities(
            backendId = FAST_CHECK_ID,
            backendVersion = FAST_CHECK_VERSION,
            capabilities = listOf(
                approximate("inputs[0].domain", "domain.number.approximate"),
                unsupported("inputs[1].domain", "domain.object.unsupported"),
            ),
        )

        assertEquals(ProjectionLevel.UNSUPPORTED, capability.level)
    }

    @Test
    fun `non exact capability requires a diagnostic reason`() {
        assertFailsWith<IllegalArgumentException> {
            ProjectionCapability(
                backendId = FAST_CHECK_ID,
                backendVersion = FAST_CHECK_VERSION,
                level = ProjectionLevel.APPROXIMATE,
            )
        }
    }

    @Test
    fun `supported concrete and unsupported symbolic is concrete only`() {
        assertEquals(
            PropertyCapabilityLevel.CONCRETE_ONLY,
            classifyPropertyCapability(
                concrete = exact(),
                symbolic = unsupported("predicate", "entrypoint.async", backendId = "usvm"),
            ),
        )
    }

    @Test
    fun `property classification accounts for both projections`() {
        assertEquals(
            PropertyCapabilityLevel.EXACT,
            classifyPropertyCapability(exact(), exact(backendId = "usvm")),
        )
        assertEquals(
            PropertyCapabilityLevel.APPROXIMATE,
            classifyPropertyCapability(
                exact(),
                approximate("inputs[0].domain", "domain.approximate", backendId = "usvm"),
            ),
        )
        assertEquals(
            PropertyCapabilityLevel.UNSUPPORTED,
            classifyPropertyCapability(
                unsupported("inputs[0].domain", "domain.unsupported"),
                exact(backendId = "usvm"),
            ),
        )
    }

    private fun exact(backendId: String = FAST_CHECK_ID) = ProjectionCapability(
        backendId = backendId,
        backendVersion = FAST_CHECK_VERSION,
        level = ProjectionLevel.EXACT,
    )

    private fun approximate(
        path: String,
        code: String,
        backendId: String = FAST_CHECK_ID,
    ) = ProjectionCapability(
        backendId = backendId,
        backendVersion = FAST_CHECK_VERSION,
        level = ProjectionLevel.APPROXIMATE,
        diagnostics = listOf(CapabilityDiagnostic(code, "Approximate projection", path)),
    )

    private fun unsupported(
        path: String,
        code: String,
        backendId: String = FAST_CHECK_ID,
    ) = ProjectionCapability(
        backendId = backendId,
        backendVersion = FAST_CHECK_VERSION,
        level = ProjectionLevel.UNSUPPORTED,
        diagnostics = listOf(CapabilityDiagnostic(code, "Unsupported projection", path)),
    )

    private companion object {
        const val FAST_CHECK_ID = "fast-check"
        const val FAST_CHECK_VERSION = "4.9.0"
    }
}
