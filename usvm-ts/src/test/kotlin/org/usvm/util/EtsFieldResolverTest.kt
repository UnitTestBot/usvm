package org.usvm.util

import io.mockk.mockk
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.junit.jupiter.api.Test
import org.usvm.machine.TsContext
import kotlin.test.assertEquals

class EtsFieldResolverTest {
    @Test
    fun `missing built-in class leaves its field unresolved`() {
        val scene = EtsScene(projectFiles = emptyList())
        val ctx = TsContext(scene = scene, components = mockk())
        val hierarchy = EtsHierarchy(scene)

        for ((className, fieldName) in listOf("RangeError" to "stack", "URL" to "searchParams")) {
            val signature = EtsClassSignature(
                name = className,
                file = EtsFileSignature.UNKNOWN,
            )
            val field = EtsFieldSignature(
                enclosingClass = signature,
                name = fieldName,
                type = EtsStringType,
            )

            val result = ctx.resolveEtsField(
                instance = null,
                field = field,
                hierarchy = hierarchy,
            )

            assertEquals(TsResolutionResult.Empty, result)
        }
    }
}
