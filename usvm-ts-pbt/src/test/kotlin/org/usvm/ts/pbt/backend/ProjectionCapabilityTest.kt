package org.usvm.ts.pbt.backend

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectionCapabilityTest {
    @Test
    fun `least capable nested projection wins and diagnostics are deterministic`() {
        val capability = aggregateProjectionCapabilities(
            capabilities = listOf(
                exact(),
                approximate(path = "inputs[1].domain", code = "domain.string.approximate"),
                approximate(path = "inputs[0].domain", code = "domain.number.approximate"),
            ),
        )

        assertEquals(ProjectionLevel.APPROXIMATE, capability.level)
        assertEquals(
            listOf("domain.number.approximate", "domain.string.approximate"),
            capability.diagnostics.map { it.code },
        )
    }

    @Test
    fun `unsupported nested projection wins over approximate projection`() {
        val capability = aggregateProjectionCapabilities(
            capabilities = listOf(
                approximate(path = "inputs[0].domain", code = "domain.number.approximate"),
                unsupported(path = "inputs[1].domain", code = "domain.object.unsupported"),
            ),
        )

        assertEquals(ProjectionLevel.UNSUPPORTED, capability.level)
    }

    @Test
    fun `non exact capability requires a diagnostic reason`() {
        assertFailsWith<IllegalArgumentException> {
            ProjectionCapability(
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
                symbolic = unsupported(path = "predicate", code = "entrypoint.async"),
            ),
        )
    }

    @Test
    fun `property classification accounts for both projections`() {
        assertEquals(
            PropertyCapabilityLevel.EXACT,
            classifyPropertyCapability(exact(), exact()),
        )

        assertEquals(
            PropertyCapabilityLevel.APPROXIMATE,
            classifyPropertyCapability(
                exact(),
                approximate(path = "inputs[0].domain", code = "domain.approximate"),
            ),
        )

        assertEquals(
            PropertyCapabilityLevel.UNSUPPORTED,
            classifyPropertyCapability(
                unsupported(path = "inputs[0].domain", code = "domain.unsupported"),
                exact(),
            ),
        )
    }

    private fun exact() = ProjectionCapability(
        level = ProjectionLevel.EXACT,
    )

    private fun approximate(
        path: String,
        code: String,
    ) = ProjectionCapability(
        level = ProjectionLevel.APPROXIMATE,
        diagnostics = listOf(
            CapabilityDiagnostic(
                code = code,
                message = "Approximate projection",
                path = path,
            ),
        ),
    )

    private fun unsupported(
        path: String,
        code: String,
    ) = ProjectionCapability(
        level = ProjectionLevel.UNSUPPORTED,
        diagnostics = listOf(
            CapabilityDiagnostic(
                code = code,
                message = "Unsupported projection",
                path = path,
            ),
        ),
    )
}
