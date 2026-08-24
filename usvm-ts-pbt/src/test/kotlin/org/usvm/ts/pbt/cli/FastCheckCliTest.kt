package org.usvm.ts.pbt.cli

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyBasedTestingBackend
import org.usvm.ts.pbt.backend.PropertyFailureDetails
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.BooleanDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.registry.PropertyRegistry
import org.usvm.ts.pbt.registry.PropertyRegistryProvider
import org.usvm.ts.pbt.testResourcesRoot
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FastCheckCliTest {
    @Test
    fun `help describes options declared by the CLI parser`() {
        val output = StringBuilder()
        val errors = StringBuilder()

        val exitCode = cli(
            providers = listOf(provider(registryId = "examples", propertyIds = arrayOf("property"))),
            output = output,
            errors = errors,
        ).run(arrayOf("--help"))

        assertEquals(0, exitCode)
        assertEquals("", errors.toString())
        assertContains(output, "TypeScript source root")
        assertContains(output, "--examples")
    }

    @Test
    fun `selects one property and writes a structured success result`() {
        val output = StringBuilder()
        val errors = StringBuilder()
        val cli = cli(
            providers = listOf(
                provider(registryId = "examples", propertyIds = arrayOf("second", "first")),
            ),
            output = output,
            errors = errors,
        )

        val exitCode = cli.run(
            arrayOf(
                "--source-root", sourceRoot.toString(),
                "--registry", "examples",
                "--property", "first",
                "--seed", "42",
                "--num-runs", "7",
            ),
        )

        val results = PropertyManifestJson.json.parseToJsonElement(output.toString()).jsonArray
        val result = results.single().jsonObject

        assertEquals(0, exitCode)
        assertEquals("", errors.toString())
        assertEquals(1, results.size)
        assertEquals("first", result.getValue("propertyId").jsonPrimitive.content)
        assertEquals("success", result.getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `accepts run controls above the former policy caps`() {
        val output = StringBuilder()

        val exitCode = cli(
            providers = listOf(provider(registryId = "examples", propertyIds = arrayOf("property"))),
            output = output,
        ).run(
            arrayOf(
                "--source-root",
                sourceRoot.toString(),
                "--num-runs",
                "10001",
                "--timeout-ms",
                "86400001",
            ),
        )

        assertEquals(0, exitCode)
    }

    @Test
    fun `runs selected registries in deterministic order and returns one for a property failure`() {
        val output = StringBuilder()
        val cli = cli(
            providers = listOf(
                provider(registryId = "z-registry", propertyIds = arrayOf("z-property")),
                provider(registryId = "a-registry", propertyIds = arrayOf("passing", "failing")),
            ),
            output = output,
            failures = setOf(PropertyId("failing")),
        )

        val exitCode = cli.run(
            arrayOf(
                "--source-root",
                sourceRoot.toString(),
                "--registry",
                "a-registry",
            ),
        )

        val propertyIds = PropertyManifestJson.json.parseToJsonElement(output.toString())
            .jsonArray
            .map { result -> result.jsonObject.getValue("propertyId").jsonPrimitive.content }

        assertEquals(1, exitCode)
        assertEquals(listOf("passing", "failing"), propertyIds)
    }

    @Test
    fun `reports unknown registries and duplicate property ids as CLI errors`() {
        val unknownErrors = StringBuilder()

        val unknownExit = cli(
            providers = listOf(provider(registryId = "known", propertyIds = arrayOf("property"))),
            errors = unknownErrors,
        ).run(
            arrayOf(
                "--source-root",
                sourceRoot.toString(),
                "--registry",
                "missing",
            ),
        )

        assertEquals(2, unknownExit)
        assertEquals("cli.registry.unknown", diagnosticCode(unknownErrors))

        val duplicateErrors = StringBuilder()

        val duplicateExit = cli(
            providers = listOf(
                provider(registryId = "first-registry", propertyIds = arrayOf("shared")),
                provider(registryId = "second-registry", propertyIds = arrayOf("shared")),
            ),
            errors = duplicateErrors,
        ).run(arrayOf("--source-root", sourceRoot.toString()))

        assertEquals(2, duplicateExit)
        assertEquals("registry.property-id.duplicate", diagnosticCode(duplicateErrors))
    }

    @Test
    fun `requires source roots and a single property for replay controls`() {
        val missingRootErrors = StringBuilder()

        val missingRootExit = cli(
            providers = listOf(provider(registryId = "examples", propertyIds = arrayOf("property"))),
            errors = missingRootErrors,
        ).run(emptyArray())

        assertEquals(2, missingRootExit)
        assertEquals("cli.source-root.required", diagnosticCode(missingRootErrors))

        val replayErrors = StringBuilder()

        val replayExit = cli(
            providers = listOf(
                provider(registryId = "examples", propertyIds = arrayOf("first", "second")),
            ),
            errors = replayErrors,
        ).run(
            arrayOf(
                "--source-root",
                sourceRoot.toString(),
                "--path",
                "1:0",
            ),
        )

        assertEquals(2, replayExit)
        assertEquals("cli.single-property.required", diagnosticCode(replayErrors))
    }

    @Test
    fun `reports service loading failures as CLI errors`() {
        val serviceRoot = Files.createTempDirectory("usvm-invalid-property-service-")
        val serviceFile = serviceRoot.resolve(
            "META-INF/services/org.usvm.ts.pbt.registry.PropertyRegistryProvider",
        )

        Files.createDirectories(serviceFile.parent)
        Files.writeString(serviceFile, "missing.InvalidPropertyRegistryProvider\n")

        val errors = StringBuilder()
        val thread = Thread.currentThread()
        val previousClassLoader = thread.contextClassLoader

        try {
            URLClassLoader(arrayOf(serviceRoot.toUri().toURL()), previousClassLoader).use { classLoader ->
                thread.contextClassLoader = classLoader

                val exitCode = runCatching {
                    FastCheckCli(errors = errors).run(
                        arrayOf("--source-root", sourceRoot.toString()),
                    )
                }.getOrNull()

                assertEquals(2, exitCode)
                assertEquals("registry.provider.load.failed", diagnosticCode(errors))
            }
        } finally {
            thread.contextClassLoader = previousClassLoader
            serviceRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `reports provider failures as CLI errors`() {
        val errors = StringBuilder()
        val failingProvider = object : PropertyRegistryProvider {
            override val registryId: String = "failing"

            override fun load(): PropertyRegistry = error("provider failed")
        }

        val exitCode = runCatching {
            cli(
                providers = listOf(failingProvider),
                errors = errors,
            ).run(arrayOf("--source-root", sourceRoot.toString()))
        }.getOrNull()

        assertEquals(2, exitCode)
        assertEquals("registry.provider.load.failed", diagnosticCode(errors))
    }

    @Test
    fun `reports provider identity and linkage failures as CLI errors`() {
        val invalidProviders = listOf(
            object : PropertyRegistryProvider {
                override val registryId: String
                    get() = error("registry ID failed")

                override fun load(): PropertyRegistry = error("unreachable")
            },
            object : PropertyRegistryProvider {
                override val registryId: String = "missing-dependency"

                override fun load(): PropertyRegistry = throw NoClassDefFoundError("provider dependency")
            },
        )

        invalidProviders.forEach { provider ->
            val errors = StringBuilder()

            val exitCode = runCatching {
                cli(
                    providers = listOf(provider),
                    errors = errors,
                ).run(arrayOf("--source-root", sourceRoot.toString()))
            }.getOrNull()

            assertEquals(2, exitCode)
            assertEquals("registry.provider.load.failed", diagnosticCode(errors))
        }
    }

    private fun cli(
        providers: List<PropertyRegistryProvider>,
        output: Appendable = StringBuilder(),
        errors: Appendable = StringBuilder(),
        failures: Set<PropertyId> = emptySet(),
    ) = FastCheckCli(
        providers = providers,
        backendFactory = { FakeBackend(failures) },
        output = output,
        errors = errors,
    )

    private fun provider(registryId: String, vararg propertyIds: String) = object : PropertyRegistryProvider {
        override val registryId: String = registryId

        override fun load(): PropertyRegistry = PropertyRegistry(propertyIds.map(::property))
    }

    private fun property(id: String) = PropertyDefinition(
        id = PropertyId(id),
        inputs = listOf(PropertyInput(name = "value", domain = BooleanDomain)),
        predicate = TypeScriptEntryPoint(
            module = "properties.ts",
            exportName = "predicate",
        ),
    )

    private fun diagnosticCode(errors: StringBuilder): String = PropertyManifestJson.json
        .parseToJsonElement(errors.toString())
        .jsonObject
        .getValue("code")
        .jsonPrimitive
        .content

    private class FakeBackend(private val failures: Set<PropertyId>) : PropertyBasedTestingBackend {
        override fun run(
            property: PropertyDefinition,
            configuration: PropertyRunConfiguration,
        ): PropertyRunResult {
            val failed = property.id in failures

            return PropertyRunResult(
                propertyId = property.id,
                status = if (failed) PropertyRunStatus.FAILURE else PropertyRunStatus.SUCCESS,
                seed = configuration.seed ?: 123,
                replayPath = if (failed) "0" else null,
                counterexample = if (failed) listOf(JsConcreteValue.Boolean(false)) else null,
                numRuns = configuration.numRuns,
                numSkips = 0,
                numShrinks = if (failed) 1 else 0,
                failure = if (failed) {
                    PropertyFailureDetails(
                        kind = PropertyFailureKind.PROPERTY,
                        errorName = "PropertyFailure",
                        message = "predicate returned false",
                    )
                } else {
                    null
                },
                executionTimeMillis = 1,
            )
        }
    }

    private companion object {
        val sourceRoot: Path = testResourcesRoot()
    }
}
