package org.usvm.machine.expr

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

class SourceSdkConstructorShadowTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/models/SourceSdkConstructorShadow.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))

    @Test
    fun `file global source methods retain their implementation before SDK constructor matching`() {
        for (name in methods) {
            discoverProperties<TsTestValue.TsNumber>(
                method = getMethod(methodName = name, className = "SourceSdkConstructorShadow"),
                { result -> result.number == 1.0 },
                invariants = arrayOf({ result -> result.number == 1.0 }),
            )
        }
    }

    @Test
    fun `source constructor shadows agree with native JavaScript`() {
        val script = buildString {
            appendLine(source.readText())
            for (name in methods) {
                appendLine("if (SourceSdkConstructorShadow.$name() !== 1) throw Error('$name');")
            }
        }

        assertNodeReplay(
            source = script,
            directory = directory,
            name = "source-sdk-constructor-shadow",
            timeoutMessage = "Source constructor shadow replay timed out",
        )
    }

    private val methods = listOf("objectMethodKeepsSourceImplementation", "arrayMethodKeepsSourceImplementation")
}
