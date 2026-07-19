package org.usvm.machine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TsOptionsTest {
    @Test
    fun `symbolic progress stopping is opt in`() {
        val options = TsOptions()

        assertFalse(options.symbolicProgressStop)
        assertNull(options.progressTimeout)
        assertFalse(options.tsTargetReachabilityPruning)
    }

    @Test
    fun `enabled progress stopping requires a positive finite duration`() {
        assertFailsWith<IllegalArgumentException> {
            TsOptions(symbolicProgressStop = true)
        }
        assertFailsWith<IllegalArgumentException> {
            TsOptions(symbolicProgressStop = true, progressTimeout = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            TsOptions(symbolicProgressStop = true, progressTimeout = Duration.INFINITE)
        }

        val options = TsOptions(symbolicProgressStop = true, progressTimeout = 3.seconds)
        assertEquals(3.seconds, options.progressTimeout)
    }
}
