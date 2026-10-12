package org.usvm.samples.types

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText

class RuntimeBuiltinInstanceofTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val tsPath = "/samples/types/RuntimeBuiltinInstanceof.ts"
    override val scene: EtsScene = loadScene(tsPath)

    @Test
    fun `core SDK prototype identities match JavaScript membership`() {
        val expected = mapOf(
            "constructorObject" to 1.0,
            "constructorFunction" to 1.0,
            "constructorArray" to 0.0,
            "sdkConstructorFunction" to 1.0,
            "sdkConstructorObject" to 1.0,
            "sdkConstructorArray" to 0.0,
            "functionObject" to 1.0,
            "functionFunction" to 1.0,
            "functionProjectClass" to 0.0,
            "boxedConstructorFunction" to 1.0,
            "boxedFunctionFunction" to 1.0,
            "instanceObject" to 1.0,
            "instanceFunction" to 0.0,
            "arrayObject" to 1.0,
            "arrayArray" to 1.0,
            "primitiveObject" to 0.0,
            "nullObject" to 0.0,
            "stringObject" to 0.0,
            "localShadow" to 0.0,
            "callParameterShadow" to 0.0,
        )

        expected.forEach { (methodName, result) ->
            discoverProperties<TsTestValue.TsNumber>(
                method = getMethod(methodName = methodName, className = "RuntimeBuiltinInstanceof"),
                { value -> value.number == result },
                invariants = arrayOf({ value -> value.number == result }),
            )
        }

        replay(expected.map { (methodName, result) ->
            "if (new RuntimeBuiltinInstanceof().$methodName() !== ${result.toInt()}) throw Error('$methodName');"
        })
    }

    @Test
    fun `conditional SDK constructor preserves both selected identities`() {
        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = getMethod(methodName = "conditionalBuiltin", className = "RuntimeBuiltinInstanceof"),
            { input, result -> input.value && result.number == 1.0 },
            { input, result -> !input.value && result.number == 1.0 },
            invariants = arrayOf({ _, result -> result.number == 1.0 }),
        )

        replay(listOf(
            "if (new RuntimeBuiltinInstanceof().conditionalBuiltin(true) !== 1) throw Error('Function alias');",
            "if (new RuntimeBuiltinInstanceof().conditionalBuiltin(false) !== 1) throw Error('Object alias');"
        ))
    }

    @Test
    fun `boxed conditional functions preserve runtime membership`() {
        val methods = listOf("boxedConstructorChoice", "boxedFunctionChoice")

        methods.forEach { methodName ->
            discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
                method = getMethod(methodName = methodName, className = "RuntimeBuiltinInstanceof"),
                { input, result -> input.value && result.number == 1.0 },
                { input, result -> !input.value && result.number == 0.0 },
                invariants = arrayOf({ input, result -> result.number == if (input.value) 1.0 else 0.0 }),
            )
        }

        replay(methods.flatMap { methodName ->
            listOf(
                "if (new RuntimeBuiltinInstanceof().$methodName(true) !== 1) throw Error('$methodName(true)');",
                "if (new RuntimeBuiltinInstanceof().$methodName(false) !== 0) throw Error('$methodName(false)');",
            )
        })
    }

    private fun replay(assertions: List<String>) {
        val source = getResourcePath(tsPath).readText() + "\n" + assertions.joinToString(separator = "\n")

        assertNodeReplay(
            source = source,
            directory = directory,
            name = "runtime-builtin-instanceof",
            timeoutMessage = "Core SDK Node replay timed out",
        )
    }
}
