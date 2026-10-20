package org.usvm.util

import org.usvm.api.TsTestValue
import kotlin.test.Test

class TsMergedWitnessResolverTest : TsMethodTestRunner() {
    override val scene = loadScene("/models/MergedWitness.ts")

    @Test
    fun `interface before class resolves the runtime instance`() {
        checkWitness(methodName = "first", expected = 17.0)
    }

    @Test
    fun `interface after class resolves the runtime instance`() {
        checkWitness(methodName = "last", expected = 19.0)
    }

    private fun checkWitness(methodName: String, expected: Double) {
        val method = scene.projectClasses.flatMap { it.methods }.single { it.name == methodName }

        discoverProperties<TsTestValue.TsClass>(
            method = method,
            { result -> (result.properties["value"] as? TsTestValue.TsNumber)?.number == expected },
            invariants = arrayOf({ result ->
                (result.properties["value"] as? TsTestValue.TsNumber)?.number == expected
            }),
        )
    }
}
