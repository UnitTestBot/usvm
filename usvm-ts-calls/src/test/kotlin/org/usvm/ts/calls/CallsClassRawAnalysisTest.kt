package org.usvm.ts.calls

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsSourceSpan
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsUndefinedType
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CallsClassRawAnalysisTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `unknown hit invalidates the frozen universe even if its callback failure is caught`() {
        val admitted = statement(methodName = "read")
        val unexpected = statement(methodName = "%AM0\$read")
        val universe = mutableListOf(point(admitted))
        val observer = ClassRawAnalysisObserver(sourceFile = "Target.ts", universe = universe, output = directory)

        observer.recordOriginalEntry(admitted)
        universe += point(unexpected)
        val callbackFailure = assertFailsWith<IllegalStateException> { observer.recordOriginalEntry(unexpected) }
        val resultFailure = assertFailsWith<IllegalStateException> {
            observer.result(manifest = manifest(), analysisElapsedMillis = 0)
        }

        assertTrue(callbackFailure.message.orEmpty().contains("outside its frozen universe"))
        assertTrue(resultFailure.message.orEmpty().contains("outside their frozen universe"))
        assertTrue(resultFailure.message.orEmpty().contains("%AM0\$read"))
    }

    private fun statement(methodName: String): EtsReturnStmt {
        val owner = EtsClassSignature(
            name = "Target",
            file = EtsFileSignature(projectName = "fixture", fileName = "Target.ts"),
        )
        val method = EtsMethodImpl(
            EtsMethodSignature(
                enclosingClass = owner,
                name = methodName,
                parameters = emptyList(),
                returnType = EtsUndefinedType,
            ),
        )
        val location = EtsStmtLocation(method = method, index = 0)
        location.origin = EtsSourceSpan(
            fileName = "Target.ts", startOffset = 0, endOffset = 7,
            startLine = 0, startColumn = 0, endLine = 0, endColumn = 7, nodeKind = "ReturnStatement",
        )
        return EtsReturnStmt(location = location, returnValue = null)
    }

    private fun point(stmt: EtsReturnStmt): CallsClassRawIrPoint = CallsClassRawIrPoint(
        method = stmt.location.method.signature.toString(),
        statementIndex = 0,
        sourceFile = "Target.ts",
        startOffset = 0,
        endOffset = 7,
        nodeKind = "ReturnStatement",
    )

    private fun manifest(): CallsClassStartManifest = CallsClassStartManifest(
        toolRevision = "fixture",
        nativeFrontendRevision = "fixture",
        ledgerSha256 = "b".repeat(64),
        expectedClassCount = 1,
        classes = listOf(
            CallsClassStartCase(
                classId = "target",
                identity = "fixture@Target",
                projectRevision = "a".repeat(40),
                sourceRoot = directory.toString(),
                sourceFile = "Target.ts",
                className = "Target",
                inputSha256 = mapOf("Target.ts" to "c".repeat(64)),
            ),
        ),
    )
}
