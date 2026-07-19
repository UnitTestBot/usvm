package org.usvm.machine.expr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolicSemanticTelemetryTest {
    @Test
    fun `reason codes are stable unique snake case values`() {
        val codes = SymbolicSemanticReason.entries.map(SymbolicSemanticReason::code)

        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("[a-z][a-z0-9_]+")) })
        assertTrue("unresolved_pointer_call_mock" in codes)
        assertTrue("module_namespace_binding_not_materialized" in codes)
    }
}
