package org.usvm.samples.lang

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.test.util.checkers.eq as exactly

class VoidAssignment : TsMethodTestRunner() {
    override val scene: EtsScene = loadScene("/samples/lang/VoidAssignment.ts")

    @Test
    fun `assigned void call returns undefined after its mutation`() {
        val method = getMethod("assignmentResult")

        checkMatches<TsTestValue.TsNumber>(
            method = method,
            analysisResultNumberMatches = exactly(1),
            { result -> result eq 1 },
        )
    }
}
