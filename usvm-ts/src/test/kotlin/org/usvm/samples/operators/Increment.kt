package org.usvm.samples.operators

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq

class Increment : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/operators/Increment.ts")

    @Test
    fun `pre increment returns and stores the incremented value`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("preIncrement"),
            { result -> result eq 22 },
        )
    }

    @Test
    fun `post increment returns the old value and stores the incremented value`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("postIncrement"),
            { result -> result eq 12 },
        )
    }

    @Test
    fun `pre decrement returns and stores the decremented value`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("preDecrement"),
            { result -> result eq 0 },
        )
    }

    @Test
    fun `post decrement returns the old value and stores the decremented value`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("postDecrement"),
            { result -> result eq 10 },
        )
    }

    @Test
    fun `post increment converts a boolean before preserving the old numeric value`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("postIncrementBoolean"),
            { result -> result eq 12 },
            invariants = arrayOf({ result -> result eq 12 }),
        )
    }

    @Test
    fun `post decrement converts null and writes the decremented numeric value`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("postDecrementNull"),
            { result -> result eq -1 },
            invariants = arrayOf({ result -> result eq -1 }),
        )
    }

    @Test
    fun `post increment writes the field while preserving the old numeric value`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = getMethod("postIncrementField"),
            { result -> result eq 12 },
            invariants = arrayOf({ result -> result eq 12 }),
        )
    }
}
