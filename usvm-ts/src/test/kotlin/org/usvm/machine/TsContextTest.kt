package org.usvm.machine

import io.mockk.mockk
import org.jacodb.ets.model.EtsBooleanLiteralType
import org.jacodb.ets.model.EtsNumberLiteralType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringLiteralType
import org.junit.jupiter.api.Test
import kotlin.test.assertSame

class TsContextTest {
    @Test
    fun `literal types use their base type sorts`() {
        TsContext(EtsScene(emptyList()), mockk()).use { context ->
            assertSame(context.boolSort, context.typeToSort(EtsBooleanLiteralType(true)))
            assertSame(context.fp64Sort, context.typeToSort(EtsNumberLiteralType(1.0)))
            assertSame(context.addressSort, context.typeToSort(EtsStringLiteralType("value")))
        }
    }
}
