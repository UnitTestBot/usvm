package org.usvm.machine.call

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FreshStringMockSourceTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/FreshStringMock.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(projectFiles = listOf(sourceFile))

    @Test
    fun `fresh string result has a readable private length`() {
        val values = analyze("length")

        assertTrue(values.isNotEmpty())
        values.forEach { value -> assertTrue(assertIs<TsTestValue.TsNumber>(value).number in 0.0..8.0) }
    }

    @Test
    fun `fresh string result retains backing through ordinary concatenation`() {
        val values = analyze("concat")

        assertTrue(values.isNotEmpty())
        values.forEach { value -> assertTrue(assertIs<TsTestValue.TsString>(value).value.endsWith("!")) }
    }

    private fun analyze(methodName: String): List<TsTestValue> {
        val method = scene.projectClasses.single { clazz -> clazz.name == "FreshStringMock" }
            .methods.single { candidate -> candidate.name == methodName }
        return TsMachine(
            scene = scene,
            options = UMachineOptions(throwExceptionOnStepFailure = true, stepsFromLastCovered = 128L),
            tsOptions = TsOptions(maxArraySize = 8, unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN),
        ).use { machine ->
            machine.analyze(listOf(method)).map { state -> TsTestResolver().resolve(method, state).returnValue }
        }
    }
}
