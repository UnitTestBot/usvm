package org.usvm.machine

import io.mockk.mockk
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsIntersectionType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsUnclearRefType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TsContextTypeToSortTest {
    private val ctx = TsContext(
        scene = EtsScene(projectFiles = emptyList()),
        components = mockk(),
    )

    @Test
    fun `intersection of reference types has reference sort`() {
        val window = EtsClassType(
            signature = EtsClassSignature(
                name = "Window",
                file = EtsFileSignature.UNKNOWN,
            ),
        )
        val globalThis = EtsUnclearRefType(
            name = "globalThis",
            typeParameters = emptyList(),
        )
        val type = EtsIntersectionType(types = listOf(window, globalThis))

        val sort = ctx.typeToSort(type)

        assertEquals(ctx.addressSort, sort)
    }

    @Test
    fun `intersection with incompatible sorts remains unresolved`() {
        val globalThis = EtsUnclearRefType(
            name = "globalThis",
            typeParameters = emptyList(),
        )
        val type = EtsIntersectionType(types = listOf(EtsNumberType, globalThis))

        val sort = ctx.typeToSort(type)

        assertEquals(ctx.unresolvedSort, sort)
    }
}
