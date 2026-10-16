package org.usvm.ts.pbt.cli

import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CampaignFrontendPreflightTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `preflight distinguishes a retained executable class from a missing class without changing source`() {
        val root = testResourcesRoot().resolve("properties/campaign")
        val source = root.resolve("CompletedSubject.ts")
        val original = Files.readString(source)
        val property = PropertyDefinition(
            id = PropertyId(value = "preflight.healthy"),
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = -100, max = 100))),
            predicate = TypeScriptEntryPoint(module = "CompletedProperties.ts", exportName = "healthy"),
        )
        val propertyJson = PropertyManifestJson.json.encodeToJsonElement(property)
        val rootJson = PropertyManifestJson.json.encodeToJsonElement(root.toString())
        val request = directory.resolve("case.json")
        val destination = directory.resolve("preflight.json")

        for ((className, expectedExit) in listOf("BranchingCounter" to 0, "MissingClass" to 2)) {
            Files.writeString(
                request,
                """{"sourceRoot":$rootJson,"classSource":"CompletedSubject.ts","className":"$className",
                "property":$propertyJson} """,
            )

            val exit = CampaignFrontendPreflight.run(
                args = arrayOf(request.toString(), destination.toString()),
                errors = StringBuilder(),
            )
            val result = PropertyManifestJson.json.parseToJsonElement(Files.readString(destination)).jsonObject

            assertEquals(expectedExit, exit)
            assertEquals(expectedExit == 0, result.getValue("supported").jsonPrimitive.content.toBooleanStrict())
            assertEquals(original, Files.readString(source))
            if (expectedExit == 0) {
                assertTrue(result.getValue("classStatements").jsonPrimitive.content.toInt() > 0)
            }
        }
    }
}
