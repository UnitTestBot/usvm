package org.usvm.machine.call

import io.mockk.mockk
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsScene
import org.usvm.UMachineOptions
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TsUnknownCallModelRegistryTest {
    @Test
    fun `descriptor IDs and supported domains must be non blank`() {
        assertFailsWith<IllegalArgumentException> {
            descriptor(id = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            descriptor(id = "model", domainId = "")
        }
        assertFailsWith<IllegalArgumentException> {
            descriptor(id = "model", domainDescription = " ")
        }
    }

    @Test
    fun `duplicate IDs are rejected`() {
        val error = assertFailsWith<IllegalArgumentException> {
            TsUnknownCallModelRegistry(
                registrations = listOf(registration("duplicate"), registration("duplicate")),
            )
        }

        assertEquals("Duplicate semantic model IDs: duplicate", error.message)
    }

    @Test
    fun `ambiguous matches report stable sorted IDs`() {
        val registry = TsUnknownCallModelRegistry(
            registrations = listOf(registration("z-model"), registration("a-model")),
            backends = listOf(FakeBackend),
        ).freeze()

        val error = assertFailsWith<IllegalStateException> {
            registry.select(mockk())
        }

        assertEquals("Ambiguous semantic models matched: a-model, z-model", error.message)
    }

    @Test
    fun `unknown enabled IDs are rejected`() {
        val registry = TsUnknownCallModelRegistry(listOf(registration("known")))

        val error = assertFailsWith<IllegalArgumentException> {
            registry.freeze(enabledModelIds = setOf("missing"))
        }

        assertEquals("Unknown semantic model IDs: missing", error.message)
    }

    @Test
    fun `enabled implementation kinds require configured backends`() {
        val registry = TsUnknownCallModelRegistry(
            registrations = listOf(registration("model-without-backend")),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            registry.freeze()
        }

        assertEquals("Missing semantic model backends: INTRINSIC", error.message)
    }

    @Test
    fun `selection and fingerprint do not depend on registration order`() {
        val forward = listOf(
            registration(id = "a", matches = false),
            registration(id = "b", matches = true),
        )
        val call = mockk<TsUnknownCall>()

        val first = TsUnknownCallModelRegistry(
            registrations = forward,
            backends = listOf(FakeBackend),
        ).freeze()
        val second = TsUnknownCallModelRegistry(
            registrations = forward.reversed(),
            backends = listOf(FakeBackend),
        ).freeze()

        assertEquals("b", first.select(call)?.descriptor?.id)
        assertEquals("b", second.select(call)?.descriptor?.id)
        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `frozen subset is detached and changes fingerprint`() {
        val mutableIds = mutableSetOf("a")
        val registry = TsUnknownCallModelRegistry(
            registrations = listOf(registration("a"), registration("b")),
            backends = listOf(FakeBackend),
        )

        val onlyA = registry.freeze(enabledModelIds = mutableIds)
        mutableIds += "b"
        val both = registry.freeze()

        assertEquals(listOf("a"), onlyA.descriptors.map { it.id })
        assertNotEquals(onlyA.fingerprint, both.fingerprint)
        assertTrue(onlyA.fingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `same model EtsIR file reference is included once`() {
        val modelFile = etsFile(fileName = "model.ts")
        val registry = TsUnknownCallModelRegistry(
            registrations = listOf(
                registration(id = "a", implementation = FakeImplementation(listOf(modelFile))),
                registration(id = "b", implementation = FakeImplementation(listOf(modelFile))),
            ),
            backends = listOf(FakeBackend),
        ).freeze()

        assertEquals(listOf(modelFile), registry.additionalSceneFiles)
    }

    @Test
    fun `distinct model EtsIR files with the same signature are rejected`() {
        val first = etsFile(fileName = "model.ts")
        val second = etsFile(fileName = "model.ts")

        val error = assertFailsWith<IllegalArgumentException> {
            TsUnknownCallModelRegistry(
                registrations = listOf(
                    registration(id = "a", implementation = FakeImplementation(listOf(first))),
                    registration(id = "b", implementation = FakeImplementation(listOf(second))),
                ),
                backends = listOf(FakeBackend),
            ).freeze()
        }

        assertEquals("Conflicting EtsIR files share signature @test/model", error.message)
    }

    @Test
    fun `application and model EtsIR files with the same signature are rejected`() {
        val applicationFile = etsFile(fileName = "shared.ts")
        val modelFile = etsFile(fileName = "shared.ts")
        val modelProvider = object : TsUnknownCallModelProvider {
            override val additionalSceneFiles: List<EtsFile> = listOf(modelFile)

            override fun apply(state: TsState, call: TsUnknownCall): TsUnknownCallModelApplication =
                error("Model provider must not execute while constructing a machine")
        }

        val error = assertFailsWith<IllegalArgumentException> {
            TsMachine(
                scene = EtsScene(projectFiles = listOf(applicationFile)),
                options = UMachineOptions(),
                tsOptions = TsOptions(),
                unknownCallModelProvider = modelProvider,
            )
        }

        assertEquals("Conflicting EtsIR files share signature @test/shared", error.message)
    }

    private fun registration(
        id: String,
        matches: Boolean = true,
        implementation: TsUnknownCallModelImplementation = FakeImplementation(),
    ) = TsUnknownCallModelRegistration(
        descriptor = descriptor(id = id, matches = matches),
        implementation = implementation,
    )

    private fun descriptor(
        id: String,
        domainId: String = "test-domain",
        domainDescription: String = "Test-only supported domain",
        matches: Boolean = true,
    ) = TsUnknownCallModelDescriptor(
        id = id,
        matcher = TsUnknownCallModelMatcher { matches },
        supportedDomain = TsUnknownCallModelSupportedDomain(
            id = domainId,
            description = domainDescription,
        ),
        precision = TsUnknownCallModelPrecision.EXACT,
        implementationKind = TsUnknownCallModelImplementationKind.INTRINSIC,
    )

    private class FakeImplementation(
        override val additionalSceneFiles: List<EtsFile> = emptyList(),
    ) : TsUnknownCallModelImplementation {
        override val kind: TsUnknownCallModelImplementationKind =
            TsUnknownCallModelImplementationKind.INTRINSIC
    }

    private object FakeBackend : TsUnknownCallModelBackend {
        override val kind: TsUnknownCallModelImplementationKind =
            TsUnknownCallModelImplementationKind.INTRINSIC

        override fun execute(
            implementation: TsUnknownCallModelImplementation,
            precision: TsUnknownCallModelPrecision,
            state: TsState,
            call: TsUnknownCall,
        ): TsUnknownCallModelBackendResult = error("Fake backend must not execute in registry metadata tests")
    }

    private fun etsFile(fileName: String): EtsFile = EtsFile(
        signature = EtsFileSignature(projectName = "test", fileName = fileName),
        classes = emptyList(),
        namespaces = emptyList(),
    )
}
