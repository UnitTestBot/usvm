package org.usvm.constraints

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USizeSort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.isEmpty
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EqualityConstraintsTests {
    private lateinit var ctx: UContext<USizeSort>
    private lateinit var ownership: MutabilityOwnership
    private lateinit var constraints: UEqualityConstraints

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        ownership = MutabilityOwnership()
        constraints = UEqualityConstraints(ctx, ownership)
    }

    @Test
    fun testDiseq() {
        val ref1 = ctx.mkRegisterReading(1, ctx.addressSort)
        val ref2 = ctx.mkRegisterReading(2, ctx.addressSort)
        val ref3 = ctx.mkRegisterReading(3, ctx.addressSort)
        val ref4 = ctx.mkRegisterReading(4, ctx.addressSort)
        val ref5 = ctx.mkRegisterReading(5, ctx.addressSort)
        val ref6 = ctx.mkRegisterReading(6, ctx.addressSort)
        val ref7 = ctx.mkRegisterReading(7, ctx.addressSort)

        constraints.makeNonEqual(ref1, ctx.nullRef)
        constraints.makeNonEqual(ref2, ctx.nullRef)
        constraints.makeNonEqual(ref3, ctx.nullRef)
        // First, init 2 distinct non-null addresses
        constraints.makeNonEqual(ref1, ref2)
        // Add ref2 != ref3
        constraints.makeNonEqual(ref2, ref3)
        // ref1 still can be equal to ref3
        assertSame(3, constraints.distinctReferences.calculateSize())
        assertTrue(constraints.referenceDisequalities[ref2]!!.contains(ref3))
        assertTrue(constraints.referenceDisequalities[ref3]!!.contains(ref2))

        constraints.makeNonEqual(ref1, ref3)
        // Now ref1, ref2 and ref3 are guaranteed to be distinct
        assertSame(4, constraints.distinctReferences.calculateSize())
        assertTrue(constraints.referenceDisequalities.all { it.value.isEmpty() })

        // Adding some entry into referenceDisequalities
        constraints.makeNonEqual(ref1, ref6)

        constraints.makeEqual(ref4, ref5)
        constraints.makeEqual(ref5, ref1)
        // Engine should be able to infer that ref5 = ref1 != ref3
        assertTrue(constraints.areDistinct(ref5, ref3))
        // Checking that ref5 = ref4 = ref1 != ref6
        assertTrue(constraints.areDistinct(ref5, ref6))

        val repr = constraints.findRepresentative(ref1)
        if (repr != ref1) {
            // Here we check the invariant of distinctReferences (see docs for UEqualityConstraints)
            assertTrue(!constraints.distinctReferences.contains(ref1))
            assertTrue(constraints.distinctReferences.contains(repr))
            val ref6Diseq = constraints.referenceDisequalities[ref6]!!
            assertTrue(ref6Diseq.contains(repr))
            assertTrue(!ref6Diseq.contains(ref1))
        }

        assertTrue(!constraints.isContradicting)
        constraints.makeEqual(ref7, ref3)
        assertTrue(!constraints.isContradicting)
        constraints.makeEqual(ref7, ref4)
        // Check that we've detected the conflict ref4 = ref5 = ref1 != ref3 = ref7 = ref4
        assertTrue(constraints.isContradicting)
    }

    @Test
    fun testNullableDiseq() {
        val ref1 = ctx.mkRegisterReading(1, ctx.addressSort)
        val ref2 = ctx.mkRegisterReading(2, ctx.addressSort)
        val ref3 = ctx.mkRegisterReading(3, ctx.addressSort)
        val ref4 = ctx.mkRegisterReading(4, ctx.addressSort)

        constraints.makeNonEqualOrBothNull(ref1, ref2)
        constraints.makeNonEqualOrBothNull(ref1, ref3)

        // For now, we have two constraints:
        // (1) (ref1 != ref2) || (ref1 == ref2 == null)
        // (2) (ref1 != ref3) || (ref1 == ref3 == null)
        // Adding constraint ref1 == ref2.
        // Testing that equality constraints infer that both (ref1 == null) and (ref2 == null).
        // Furthermore, inferring that ref1 == null should simplify constraint (2) to true
        constraints.makeEqual(ref1, ref2)
        assertFalse(constraints.nullableDisequalities.containsKey(ref1))
        assertTrue(constraints.areEqual(ref1, ctx.nullRef))
        assertTrue(constraints.areEqual(ref2, ctx.nullRef))
        assertFalse(constraints.areDistinct(ref1, ref3))
        assertFalse(constraints.nullableDisequalities[ref3]?.contains(ref1) ?: false)

        constraints.makeNonEqual(ref4, ctx.nullRef)
        constraints.makeNonEqualOrBothNull(ref3, ref4)
        // Now, we've added 2 more constraints:
        // (3) ref4 != null
        // (4) ref3 != ref4 || (ref3 == ref4 == null)
        // These two should be automatically simplified to ref3 != ref4.
        assertSame(2, constraints.distinctReferences.calculateSize())
        constraints.makeNonEqual(ref3, ctx.nullRef)
        // Now we have obtained that null, ref3 and ref4 are 3 distinct references. This should be represented as clique
        // constraint...
        assertSame(3, constraints.distinctReferences.calculateSize())
    }
}
