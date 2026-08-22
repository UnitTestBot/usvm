package org.usvm.frontend

import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.util.getResourcePath
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeTsFrontendDefaultTest {
    @Test
    fun `loads TypeScript without an external frontend`() {
        val file = loadEtsFileAutoConvert(
            getResourcePath("/samples/lang/Numeric.ts"),
            useArkAnalyzerTypeInference = null,
        )

        val numericClass = assertNotNull(file.classes.singleOrNull { it.name == "Numeric" })
        assertTrue(numericClass.methods.any { it.name == "numberToNumber" })
    }
}
