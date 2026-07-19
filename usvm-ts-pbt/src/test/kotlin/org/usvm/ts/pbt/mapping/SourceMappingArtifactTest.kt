package org.usvm.ts.pbt.mapping

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.external.ArtifactValidator
import org.usvm.ts.pbt.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readLines

class SourceMappingArtifactTest {
    @Test
    fun `mapping JSONL passes the frozen v2 validator and preserves origin extensions`() {
        val artifact = System.getenv("TS_PBT_SOURCE_TARGETS_UNDER_TEST")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: getResourcePath("/mapping/golden-source-targets.jsonl")

        val report = ArtifactValidator.validateSourceTargets(artifact)
        assertTrue(report.valid, report.issues.toString())
        assertTrue(report.issues.isEmpty())

        val records = artifact.readLines().filter(String::isNotBlank).map { Json.parseToJsonElement(it) }
        assertTrue(records.isNotEmpty())
        records.forEach { element ->
            val origin = element.jsonObject.getValue("sourceOrigin").jsonObject
            assertTrue(origin.getValue("sourceCallableId").jsonPrimitive.content.startsWith("ts:"))
            assertTrue("moduleOrigin" in origin)
            assertTrue("importOrigins" in origin)
            assertTrue("fileInitOrigin" in origin)
            assertTrue("callableBinding" in origin)
        }
        assertEquals(records.size, records.map { it.jsonObject.getValue("branchId") }.distinct().size)
    }
}
