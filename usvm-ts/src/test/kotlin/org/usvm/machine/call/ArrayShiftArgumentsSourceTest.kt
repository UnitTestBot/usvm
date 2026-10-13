package org.usvm.machine.call

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.io.TempDir
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test

class ArrayShiftArgumentsSourceTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/ArrayShiftIntrinsic.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))

    @Test
    fun `ignored shift arguments execute before observing the receiver`() {
        for (methodName in sideEffectMethods) {
            discoverProperties<TsTestValue.TsNumber>(
                method = getMethod(methodName = methodName, className = "ArrayShiftIntrinsic"),
                { result -> result.number == 1.0 },
                invariants = arrayOf({ result -> result.number == 1.0 }),
            )
        }
    }

    @Test
    fun `ignored shift arguments preserve native JavaScript side effects`() {
        val script = buildString {
            appendLine(source.readText())
            appendLine("const subject = new ArrayShiftIntrinsic();")
            appendLine("if (subject.shiftWithArguments() !== 48) throw Error('shiftWithArguments');")
            for (methodName in sideEffectMethods) {
                appendLine("if (subject.$methodName() !== 1) throw Error('$methodName');")
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "array-shift-ignored-arguments",
            timeoutMessage = "Array.shift argument replay timed out",
        )
    }

    private val sideEffectMethods = listOf("shiftWithArgumentSideEffects", "shiftAfterArgumentEmptiesReceiver")
}
