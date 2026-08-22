package org.usvm.ts.pbt

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsSourceSpan
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FrontendBaselineTest {
    @Test
    fun `native frontend loads a TypeScript method with source origins`() {
        val source = resourcePath("/baseline/FrontendBaseline.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val method = EtsScene(listOf(file)).projectClasses
            .flatMap { it.methods }
            .single { it.name == "absoluteValue" }

        assertTrue(method.cfg.stmts.isNotEmpty(), "absoluteValue must have a non-empty CFG")

        val origins = method.cfg.stmts.mapNotNull { it.location.origin }
        assertTrue(origins.isNotEmpty(), "absoluteValue statements must retain source origins")
        origins.forEach(::assertValidOrigin)
    }

    private fun assertValidOrigin(origin: EtsSourceSpan) {
        assertTrue(
            origin.fileName.endsWith("FrontendBaseline.ts"),
            "Unexpected source file: ${origin.fileName}",
        )
        assertTrue(origin.startOffset >= 0, "Start offset must be non-negative: $origin")
        assertTrue(origin.endOffset >= origin.startOffset, "Offsets must be ordered: $origin")
        assertTrue(origin.startLine >= 0, "Start line must be non-negative: $origin")
        assertTrue(origin.startColumn >= 0, "Start column must be non-negative: $origin")
        assertTrue(origin.endLine >= origin.startLine, "Lines must be ordered: $origin")
        if (origin.endLine == origin.startLine) {
            assertTrue(origin.endColumn >= origin.startColumn, "Columns must be ordered: $origin")
        }
        assertTrue(origin.nodeKind.isNotBlank(), "TypeScript node kind must be present: $origin")
    }

    private fun resourcePath(name: String): Path {
        val resource = assertNotNull(javaClass.getResource(name), "Missing test resource $name")
        return Paths.get(resource.toURI())
    }
}
