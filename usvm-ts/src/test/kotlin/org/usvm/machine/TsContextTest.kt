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
        TsContext(scene = EtsScene(projectFiles = emptyList()), components = mockk()).use { context ->
            val booleanType = EtsBooleanLiteralType(value = true)
            val numberType = EtsNumberLiteralType(value = 1.0)
            val stringType = EtsStringLiteralType(value = "value")

            val booleanSort = context.typeToSort(booleanType)
            val numberSort = context.typeToSort(numberType)
            val stringSort = context.typeToSort(stringType)

            assertSame(context.boolSort, booleanSort)
            assertSame(context.fp64Sort, numberSort)
            assertSame(context.addressSort, stringSort)
        }
    }
}
