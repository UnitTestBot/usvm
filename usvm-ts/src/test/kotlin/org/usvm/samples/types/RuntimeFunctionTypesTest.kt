package org.usvm.samples.types

import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.StateCollectionStrategy
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.assertNodeReplay
import org.usvm.util.eq
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText

class RuntimeFunctionTypesTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val sourcePath = "/samples/types/RuntimeFunctionTypes.ts"
    override val scene: EtsScene = loadScene(sourcePath)

    init {
        options = options.copy(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `constructor values have runtime type metadata for truthiness`() {
        checkConstant(methodName = "constructorTruthy")
    }

    @Test
    fun `named functions have runtime type metadata for truthiness`() {
        checkConstant(methodName = "namedFunctionTruthy")
    }

    @Test
    fun `closures have runtime type metadata for truthiness`() {
        val method = getMethod(methodName = "closureTruthy", className = "RuntimeFunctionTypes")

        discoverProperties<TsTestValue.TsNumber, TsTestValue.TsNumber>(
            method = method,
            { _, result -> result eq 1 },
            invariants = arrayOf({ _, result -> result eq 1 }),
        )
    }

    @Test
    fun `function and constructor references keep metadata through object fields`() {
        checkConstant(methodName = "functionInField")
        checkConstant(methodName = "constructorInField")
    }

    @Test
    fun `ordinary source functions and constructors agree with Node`() {
        val methods = listOf(
            "constructorTruthy", "namedFunctionTruthy", "functionInField", "constructorInField",
        )
        val source = buildString {
            appendLine(getResourcePath(sourcePath).readText())
            appendLine("const subject = new RuntimeFunctionTypes();")
            methods.forEach { method ->
                appendLine("if (subject.$method() !== 1) throw Error('$method');")
            }
            appendLine("if (subject.closureTruthy(7) !== 1) throw Error('closure');")
            appendLine("if (subject.conditionalFunctions(true) !== 1) throw Error('constructor branch');")
            appendLine("if (subject.conditionalFunctions(false) !== 1) throw Error('function branch');")
        }

        assertNodeReplay(
            source = source,
            directory = directory,
            name = "runtime-function-types",
            timeoutMessage = "Runtime function types replay timed out",
        )
    }

    @Test
    fun `conditional constructor and function references keep both branches`() {
        val method = getMethod(methodName = "conditionalFunctions", className = "RuntimeFunctionTypes")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue.TsNumber>(
            method = method,
            { flag, result -> flag.value && (result eq 1) },
            { flag, result -> !flag.value && (result eq 1) },
            invariants = arrayOf({ _, result -> result eq 1 }),
        )
    }

    private fun checkConstant(methodName: String) {
        val method = getMethod(methodName = methodName, className = "RuntimeFunctionTypes")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 1 },
            invariants = arrayOf({ result -> result eq 1 }),
        )
    }
}
