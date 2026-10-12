package org.usvm.samples.types

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText

class RuntimeSdkInstanceofTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/types/RuntimeSdkInstanceof.ts"
    private val sdkPath = "/samples/types/RuntimeSdkClasses.ts"
    override val scene: EtsScene = EtsScene(
        projectFiles = listOf(loadEtsFileAutoConvert(getResourcePath(tsPath))),
        sdkFiles = listOf(loadEtsFileAutoConvert(getResourcePath(sdkPath))),
    )

    @Test
    fun `explicit SDK classes retain constructor and inherited instance identities`() {
        val expected = mapOf("constructorObject" to 1.0, "constructorFunction" to 1.0, "constructorParent" to 0.0)

        expected.forEach { (methodName, result) ->
            discoverProperties<TsTestValue.TsNumber>(
                method = getMethod(methodName = methodName, className = "RuntimeSdkInstanceof"),
                { value -> value.number == result },
                invariants = arrayOf({ value -> value.number == result }),
            )
        }

        discoverProperties<TsTestValue.TsClass, TsTestValue.TsNumber>(
            method = getMethod(methodName = "inherited", className = "RuntimeSdkInstanceof"),
            { _, result -> result.number == 1.0 },
            invariants = arrayOf({ _, result -> result.number == 1.0 }),
        )

        val sdkSource = getResourcePath(sdkPath).readText().replace("export class", "class")
        val source = sdkSource + "\n" + getResourcePath(tsPath).readText().lineSequence()
            .filterNot { it.startsWith("import ") }.joinToString(separator = "\n")
        val assertions = expected.map { (methodName, result) ->
            "if (new RuntimeSdkInstanceof().$methodName() !== ${result.toInt()}) throw Error('$methodName');"
        } + "if (new RuntimeSdkInstanceof().inherited(new RuntimeSdkChild()) !== 1) throw Error('SDK inheritance');"

        assertNodeReplay(
            source = source + "\n" + assertions.joinToString(separator = "\n"),
            directory = directory,
            name = "runtime-sdk-instanceof",
            timeoutMessage = "Explicit SDK Node replay timed out",
        )
    }
}
