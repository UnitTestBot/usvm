package org.usvm.constraints

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UHeapRef
import kotlin.test.assertSame

class EqualityConstraintsTests {
    private lateinit var ctx: UContext
    private lateinit var constraints: UEqualityConstraints

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        constraints = UEqualityConstraints()
    }

    @Test
    fun testDiseq() {
        val ref1: UHeapRef = ctx.mkRegisterReading(1, ctx.addressSort)
        val ref2: UHeapRef = ctx.mkRegisterReading(2, ctx.addressSort)
        val ref3: UHeapRef = ctx.mkRegisterReading(3, ctx.addressSort)
        val ref4: UHeapRef = ctx.mkRegisterReading(4, ctx.addressSort)
        val ref5: UHeapRef = ctx.mkRegisterReading(5, ctx.addressSort)
        val ref6: UHeapRef = ctx.mkRegisterReading(6, ctx.addressSort)
        val ref7: UHeapRef = ctx.mkRegisterReading(7, ctx.addressSort)

        // First, init 2 distinct addresses
        constraints.addReferenceDisequality(ref1, ref2)
        // Add ref2 != ref3
        constraints.addReferenceDisequality(ref2, ref3)
        // ref1 still can be equal to ref3
        assertSame(constraints.distinctReferences.size, 2)
        assert(constraints.referenceDisequalities[ref2]!!.contains(ref3))
        assert(constraints.referenceDisequalities[ref3]!!.contains(ref2))

        constraints.addReferenceDisequality(ref1, ref3)
        // Now ref1, ref2 and ref3 are guaranteed to be distinct
        assertSame(constraints.distinctReferences.size, 3)
        assert(constraints.referenceDisequalities.all { it.value.isEmpty() })

        // Adding some entry into referenceDisequalities
        constraints.addReferenceDisequality(ref1, ref6)

        constraints.addReferenceEquality(ref4, ref5)
        constraints.addReferenceEquality(ref5, ref1)
        // Engine should be able to infer that ref5 = ref1 != ref3
        assert(constraints.areDistinct(ref5, ref3))
        // Checking that ref5 = ref4 = ref1 != ref6
        assert(constraints.areDistinct(ref5, ref6))

        val repr = constraints.equalReferences.find(ref1)
        if (repr != ref1) {
            // Here we check the invariant of distinctReferences (see docs for UEqualityConstraints)
            assert(!constraints.distinctReferences.contains(ref1))
            assert(constraints.distinctReferences.contains(repr))
            val ref6Diseq = constraints.referenceDisequalities[ref6]!!
            assert(ref6Diseq.contains(repr))
            assert(!ref6Diseq.contains(ref1))
        }

        assert(!constraints.isContradiction)
        constraints.addReferenceEquality(ref7, ref3)
        assert(!constraints.isContradiction)
        constraints.addReferenceEquality(ref7, ref4)
        // Check that we've detected the conflict ref4 = ref5 = ref1 != ref3 = ref7 = ref4
        assert(constraints.isContradiction)
    }
}
