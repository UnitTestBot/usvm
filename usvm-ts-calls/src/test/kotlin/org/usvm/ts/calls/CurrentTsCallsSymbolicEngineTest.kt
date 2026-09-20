package org.usvm.ts.calls

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CurrentTsCallsSymbolicEngineTest {
    @Test
    fun `bundled frontend accepts only the revision baked into the running build`() {
        val engine = CurrentTsCallsSymbolicEngine(
            environment = emptyMap<String, String>()::get,
            bundledNativeFrontendRevision = "bundled:published-jacodb",
        )

        engine.verifyNativeFrontendOnce(expectedRevision = "bundled:published-jacodb")

        assertFailsWith<IllegalArgumentException> {
            engine.verifyNativeFrontendOnce(expectedRevision = "bundled:different-jacodb")
        }
    }

    @Test
    fun `bundled frontend rejects native frontend environment overrides`() {
        val environment = mapOf("ETS_FRONTEND_DIR" to "/unused/frontend")
        val engine = CurrentTsCallsSymbolicEngine(
            environment = environment::get,
            bundledNativeFrontendRevision = "bundled:published-jacodb",
        )

        assertFailsWith<IllegalArgumentException> {
            engine.verifyNativeFrontendOnce(expectedRevision = "bundled:published-jacodb")
        }
    }
}
