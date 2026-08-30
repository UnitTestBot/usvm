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
}
