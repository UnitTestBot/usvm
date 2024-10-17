package org.usvm.memory

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.usvm.Type
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USizeSort
import org.usvm.api.readArrayIndex
import org.usvm.collection.string.getStringContent
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.UTypeConstraints
import org.usvm.mkSizeExpr
import kotlin.test.Test
import kotlin.test.assertEquals

class StringsTest {
    private lateinit var ctx: UContext<USizeSort>
    private lateinit var heap: UMemory<Type, Any>
    private lateinit var arrayType: Type
    private lateinit var sizeSort: USizeSort

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<Type, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        every { components.mkSizeExprProvider(any()) } answers { UBv32SizeExprProvider(ctx) }
        val eqConstraints = UEqualityConstraints(ctx)
        val typeConstraints = UTypeConstraints(components.mkTypeSystem(ctx), eqConstraints)
        heap = UMemory(ctx, typeConstraints)
        arrayType = mockk<Type>()
        sizeSort = ctx.mkBv32Sort()
    }

    @Test
    fun stringContentTest() {
        val string = ctx.mkStringLiteral("aba")
        val content = heap.getStringContent(string, arrayType, sizeSort, ctx.charSort)
        val a = heap.readArrayIndex(content, ctx.mkSizeExpr(0), arrayType, ctx.charSort)
        val b = heap.readArrayIndex(content, ctx.mkSizeExpr(1), arrayType, ctx.charSort)
        assertEquals(a, ctx.mkChar('a'))
        assertEquals(b, ctx.mkChar('b'))
    }
}
