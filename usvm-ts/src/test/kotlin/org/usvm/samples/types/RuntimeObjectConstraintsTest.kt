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

class RuntimeObjectConstraintsTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val sourcePath = "/samples/types/RuntimeObjectConstraints.ts"
    override val scene: EtsScene = loadScene(sourcePath)

    init {
        options = options.copy(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `property presence before instanceof retains matching and unrelated objects`() {
        checkConstraintOrder(methodName = "propertyBeforeInstance")
    }

    @Test
    fun `instanceof before property presence retains matching and unrelated objects`() {
        checkConstraintOrder(methodName = "instanceBeforeProperty")
    }

    @Test
    fun `nullish and primitive values do not become nominal objects`() {
        val method = getMethod(methodName = "nonObjectControls", className = "RuntimeObjectConstraints")

        discoverProperties<TsTestValue.TsNumber>(
            method = method,
            { result -> result eq 0 },
            invariants = arrayOf({ result -> result eq 0 }),
        )
    }

    private fun checkConstraintOrder(methodName: String) {
        val method = getMethod(methodName = methodName, className = "RuntimeObjectConstraints")

        discoverProperties<TsTestValue, TsTestValue.TsNumber>(
            method = method,
            { _, result -> result eq 1 },
            { _, result -> result eq 2 },
            { _, result -> result eq 3 },
            { _, result -> result eq 4 },
            invariants = arrayOf({ _, result -> result.number.toInt() in 1..4 }),
        )

        val source = buildString {
            appendLine(getResourcePath(sourcePath).readText())
            appendLine("const subject = new RuntimeObjectConstraints();")
            appendLine("const base = new ObjectConstraintBase();")
            appendLine("delete base.marker;")
            appendLine("if (subject.$methodName(base) !== 2) throw Error('base absent');")
            appendLine("base.marker = 7;")
            appendLine("if (subject.$methodName(base) !== 1) throw Error('base present');")
            appendLine("const child = new ObjectConstraintChild();")
            appendLine("delete child.marker;")
            appendLine("if (subject.$methodName(child) !== 2) throw Error('child absent');")
            appendLine("child.marker = 7;")
            appendLine("if (subject.$methodName(child) !== 1) throw Error('child present');")
            appendLine("const other = new ObjectConstraintOther();")
            appendLine("delete other.marker;")
            appendLine("if (subject.$methodName(other) !== 4) throw Error('unrelated absent');")
            appendLine("other.marker = 7;")
            appendLine("if (subject.$methodName(other) !== 3) throw Error('unrelated present');")
        }

        assertNodeReplay(
            source = source,
            directory = directory,
            name = methodName,
            timeoutMessage = "Runtime object constraint replay timed out",
        )
    }
}
