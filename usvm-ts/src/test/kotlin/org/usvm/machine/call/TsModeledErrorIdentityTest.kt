package org.usvm.machine.call

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature
import org.junit.jupiter.api.Test
import org.usvm.machine.TS_TYPE_ERROR_TYPE
import org.usvm.machine.expr.isModeledErrorType
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TsModeledErrorIdentityTest {
    @Test
    fun `native SDK and interpreter Error identities are recognized`() {
        val sdkError = EtsClassType(signature = EtsClassSignature.UNKNOWN.copy(name = "Error"))
        val sdkTypeError = EtsClassType(signature = EtsClassSignature.UNKNOWN.copy(name = "TypeError"))

        assertTrue(sdkError.isModeledErrorType())
        assertTrue(sdkTypeError.isModeledErrorType())
        assertTrue(TS_TYPE_ERROR_TYPE.isModeledErrorType())
    }

    @Test
    fun `same named application classes do not become builtin Error identities`() {
        val sourceFile = EtsFileSignature(projectName = "application", fileName = "ShadowErrors.ts")
        val sourceError = EtsClassType(signature = EtsClassSignature(name = "Error", file = sourceFile))
        val sourceTypeError = EtsClassType(signature = EtsClassSignature(name = "TypeError", file = sourceFile))

        assertFalse(sourceError.isModeledErrorType())
        assertFalse(sourceTypeError.isModeledErrorType())
    }
}
