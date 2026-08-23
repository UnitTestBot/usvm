package org.usvm.ts.pbt.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.usvm.ts.pbt.backend.PropertyBasedTestingBackend
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.fastcheck.PbtBackendException
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.registry.DuplicatePropertyIdException
import org.usvm.ts.pbt.registry.PropertyRegistry
import org.usvm.ts.pbt.registry.PropertyRegistryProvider
import org.usvm.ts.pbt.registry.UnknownPropertyIdException
import org.usvm.ts.pbt.validation.InvalidPropertyDefinitionException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import kotlin.system.exitProcess

/** Kotlin command-line orchestrator for service-loaded property registries and fast-check. */
class FastCheckCli(
    private val providers: List<PropertyRegistryProvider>? = null,
    private val backendFactory: (List<Path>) -> PropertyBasedTestingBackend = { sourceRoots ->
        FastCheckBackend(sourceRoots)
    },
    private val output: Appendable = System.out,
    private val errors: Appendable = System.err,
) {
    /** Runs the requested properties and returns the process exit code without terminating the JVM. */
    fun run(args: Array<String>): Int = try {
        when (val parsed = parseCliOptions(args)) {
            is CliParseResult.Help -> {
                output.appendLine(parsed.text)
                EXIT_SUCCESS
            }

            is CliParseResult.Success -> {
                execute(parsed.options)
            }
        }
    } catch (error: CliUsageException) {
        reportError(error.code, error.message.orEmpty(), error.path)
        EXIT_ERROR
    } catch (error: DuplicatePropertyIdException) {
        reportError(
            code = "registry.property-id.duplicate",
            message = error.message.orEmpty(),
            path = "properties",
            propertyId = error.propertyId.value,
        )
        EXIT_ERROR
    } catch (error: UnknownPropertyIdException) {
        reportError(
            code = "cli.property.unknown",
            message = error.message.orEmpty(),
            path = "property",
            propertyId = error.propertyId.value,
        )
        EXIT_ERROR
    } catch (error: InvalidPropertyDefinitionException) {
        reportError(
            code = "registry.property.invalid",
            message = error.message.orEmpty(),
            path = error.result.diagnostics.firstOrNull()?.path,
        )
        EXIT_ERROR
    } catch (error: PbtBackendException) {
        reportError(
            code = error.code,
            message = error.message.orEmpty(),
            path = error.path,
            propertyId = error.propertyId,
            kind = error.kind.name.lowercase(),
        )
        EXIT_ERROR
    } catch (error: ServiceConfigurationError) {
        reportError(
            code = "registry.provider.load.failed",
            message = error.message.orEmpty(),
            path = "registry",
        )
        EXIT_ERROR
    } catch (error: IllegalArgumentException) {
        reportError(
            code = "cli.argument.invalid",
            message = error.message.orEmpty(),
        )
        EXIT_ERROR
    }

    private fun execute(options: CliOptions): Int {
        val selectedProviders = selectProviders(options.registryIds)
        val registry = PropertyRegistry.combine(selectedProviders.map(::loadRegistry))
        val properties = selectProperties(registry, options.propertyId)
        requireSinglePropertyForRunScopedControls(options, properties.size)
        val examples = options.examplesFile?.let(::loadExamples).orEmpty()
        val configuration = PropertyRunConfiguration(
            seed = options.seed,
            replayPath = options.replayPath,
            numRuns = options.numRuns,
            timeoutMillis = options.timeoutMillis,
            examples = examples,
        )
        val backend = backendFactory(options.sourceRoots)
        val results = properties.map { property -> backend.run(property, configuration) }
        output.appendLine(PropertyManifestJson.json.encodeToString(results))

        val hasPropertyFailure = results.any { result -> result.status == PropertyRunStatus.FAILURE }
        return if (hasPropertyFailure) {
            EXIT_PROPERTY_FAILURE
        } else {
            EXIT_SUCCESS
        }
    }

    private fun selectProperties(
        registry: PropertyRegistry,
        propertyId: PropertyId?,
    ): List<PropertyDefinition> {
        if (propertyId != null) return listOf(registry[propertyId])

        val properties = registry.properties
        if (properties.isEmpty()) {
            throw CliUsageException(
                code = "cli.property.empty",
                message = "Selected registries contain no properties",
                path = "registry",
            )
        }
        return properties
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadRegistry(registration: ProviderRegistration): PropertyRegistry = try {
        registration.provider.load()
    } catch (error: InvalidPropertyDefinitionException) {
        throw error
    } catch (error: DuplicatePropertyIdException) {
        throw error
    } catch (error: RuntimeException) {
        throw providerFailure(registration.id, error)
    } catch (error: LinkageError) {
        throw providerFailure(registration.id, error)
    }

    private fun requireSinglePropertyForRunScopedControls(options: CliOptions, propertyCount: Int) {
        val usesRunScopedControls = options.replayPath != null || options.examplesFile != null
        if (usesRunScopedControls && propertyCount != 1) {
            throw CliUsageException(
                code = "cli.single-property.required",
                message = "Replay paths and explicit examples require exactly one selected property",
                path = "property",
            )
        }
    }

    private fun selectProviders(registryIds: List<String>): List<ProviderRegistration> {
        val availableProviders = (providers ?: loadProviders()).map(::registerProvider)
        val orderedProviders = validateProviders(availableProviders.sortedBy(ProviderRegistration::id))
        if (registryIds.isEmpty()) return orderedProviders
        val requestedIds = registryIds.toSet()
        val availableIds = orderedProviders.map(ProviderRegistration::id).toSet()
        val unknown = requestedIds.minus(availableIds).minOrNull()
        if (unknown != null) {
            throw CliUsageException(
                code = "cli.registry.unknown",
                message = "Unknown registry ID $unknown; available IDs: ${availableIds.sorted().joinToString()}",
                path = "registry",
            )
        }
        return orderedProviders.filter { registration -> registration.id in requestedIds }
    }

    private fun validateProviders(
        orderedProviders: List<ProviderRegistration>,
    ): List<ProviderRegistration> {
        orderedProviders.forEach { registration -> validateProviderId(registration.id) }
        validateUniqueProviderIds(orderedProviders)
        if (orderedProviders.isEmpty()) {
            throw CliUsageException(
                code = "cli.registry.empty",
                message = "No PropertyRegistryProvider services were found",
                path = "registry",
            )
        }
        return orderedProviders
    }

    private fun validateProviderId(providerId: String) {
        if (!REGISTRY_ID_REGEX.matches(providerId)) {
            throw CliUsageException(
                code = "cli.registry.id.invalid",
                message = "Invalid registry ID: $providerId",
                path = "registry",
            )
        }
    }

    private fun validateUniqueProviderIds(orderedProviders: List<ProviderRegistration>) {
        val duplicateRegistryId = orderedProviders
            .groupBy(ProviderRegistration::id)
            .filterValues { duplicates -> duplicates.size > 1 }
            .keys
            .minOrNull()
        if (duplicateRegistryId != null) {
            throw CliUsageException(
                code = "cli.registry.id.duplicate",
                message = "Duplicate registry ID: $duplicateRegistryId",
                path = "registry",
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun registerProvider(provider: PropertyRegistryProvider): ProviderRegistration = try {
        ProviderRegistration(
            id = provider.registryId,
            provider = provider,
        )
    } catch (error: RuntimeException) {
        throw providerFailure(provider.javaClass.name, error)
    } catch (error: LinkageError) {
        throw providerFailure(provider.javaClass.name, error)
    }

    private fun providerFailure(providerName: String, cause: Throwable) = CliUsageException(
        code = "registry.provider.load.failed",
        message = "Property registry provider $providerName failed: ${cause.message}",
        path = "registry",
        cause = cause,
    )

    private fun loadExamples(path: Path): List<List<JsConcreteValue>> = try {
        PropertyManifestJson.json.decodeFromString(Files.readString(path))
    } catch (error: IOException) {
        throw invalidExamples(path, error)
    } catch (error: SerializationException) {
        throw invalidExamples(path, error)
    }

    private fun invalidExamples(path: Path, cause: Exception) = CliUsageException(
        code = "cli.examples.invalid",
        message = "Cannot read explicit examples from $path: ${cause.message}",
        path = "examples",
        cause = cause,
    )

    private fun reportError(
        code: String,
        message: String,
        path: String? = null,
        propertyId: String? = null,
        kind: String? = null,
    ) {
        val diagnostic = CliDiagnostic(
            code = code,
            message = message,
            path = path,
            propertyId = propertyId,
            kind = kind,
        )
        errors.appendLine(PropertyManifestJson.json.encodeToString(diagnostic))
    }

    private companion object {
        const val EXIT_SUCCESS = 0
        const val EXIT_PROPERTY_FAILURE = 1
        const val EXIT_ERROR = 2

        val REGISTRY_ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9._/-]*")

        fun loadProviders(): List<PropertyRegistryProvider> = ServiceLoader
            .load(PropertyRegistryProvider::class.java)
            .toList()
    }
}

/** Process entry point used by Gradle application and installed distributions. */
fun main(args: Array<String>) {
    exitProcess(FastCheckCli().run(args))
}

@Serializable
private data class CliDiagnostic(
    val code: String,
    val message: String,
    val path: String? = null,
    val propertyId: String? = null,
    val kind: String? = null,
)

private data class ProviderRegistration(
    val id: String,
    val provider: PropertyRegistryProvider,
)
