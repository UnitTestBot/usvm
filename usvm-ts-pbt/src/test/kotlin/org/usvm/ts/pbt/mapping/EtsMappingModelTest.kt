package org.usvm.ts.pbt.mapping

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class EtsMappingModelTest {
    @Test
    fun `mapping diagnostics require a non-blank code`() {
        assertFailsWith<IllegalArgumentException> {
            EtsMappingDiagnostic(code = " ", message = "Mapping failed")
        }
    }

    @Test
    fun `mapping diagnostics require a non-blank message`() {
        assertFailsWith<IllegalArgumentException> {
            EtsMappingDiagnostic(code = "mapping.test", message = " ")
        }
    }

    @Test
    fun `exact mapping requires a target and rejects diagnostics`() {
        val diagnostic = EtsMappingDiagnostic(code = "mapping.test", message = "Mapping failed")

        assertFailsWith<IllegalArgumentException> {
            EtsMappingResult<Int>(
                status = EtsMappingStatus.EXACT,
                targets = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EtsMappingResult(
                status = EtsMappingStatus.EXACT,
                targets = listOf(1),
                diagnostics = listOf(diagnostic),
            )
        }
    }

    @Test
    fun `ambiguous mapping requires targets and a diagnostic`() {
        assertFailsWith<IllegalArgumentException> {
            EtsMappingResult(
                status = EtsMappingStatus.AMBIGUOUS,
                targets = listOf(1),
            )
        }
    }

    @Test
    fun `unmapped and unsupported mappings reject targets or missing diagnostics`() {
        val diagnostic = EtsMappingDiagnostic(code = "mapping.test", message = "Mapping failed")

        assertFailsWith<IllegalArgumentException> {
            EtsMappingResult(
                status = EtsMappingStatus.UNMAPPED,
                targets = listOf(1),
                diagnostics = listOf(diagnostic),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            EtsMappingResult<Int>(
                status = EtsMappingStatus.UNSUPPORTED,
                targets = emptyList(),
            )
        }
    }
}
