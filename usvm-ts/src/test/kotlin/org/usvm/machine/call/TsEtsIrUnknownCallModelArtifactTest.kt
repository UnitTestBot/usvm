package org.usvm.machine.call

import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TsEtsIrUnknownCallModelArtifactTest {
    private val sourcePath = getResourcePath("/models/EtsIrSemanticModels.ts")

    @Test
    fun `native frontend produces reproducible model artifacts`() {
        val first = loadEtsIrUnknownCallModelArtifact(
            sourcePath = sourcePath,
            entryPointClassName = "EtsIrSemanticModels",
            entryPointMethodName = "absolute",
        )
        val second = loadEtsIrUnknownCallModelArtifact(
            sourcePath = sourcePath,
            entryPointClassName = "EtsIrSemanticModels",
            entryPointMethodName = "absolute",
        )

        assertEquals(TsUnknownCallModelImplementationKind.ETS_IR_BODY, first.implementationKind)
        assertEquals("absolute", first.entryPoint.name)
        assertEquals(first.entryPoint.signature, second.entryPoint.signature)
        assertEquals(first.sourceHash, second.sourceHash)
        assertEquals(first.etsIrHash, second.etsIrHash)
        assertTrue(first.sourceHash.matches(Regex("[0-9a-f]{64}")))
        assertTrue(first.etsIrHash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `catalog fingerprint includes source and EtsIR hashes`() {
        val artifact = loadEtsIrUnknownCallModelArtifact(
            sourcePath = sourcePath,
            entryPointClassName = "EtsIrSemanticModels",
            entryPointMethodName = "absolute",
        )
        val originalFingerprint = fingerprint(
            implementation = TsEtsIrUnknownCallModelImplementation(artifact),
        )
        val changedSourceFingerprint = fingerprint(
            implementation = TsEtsIrUnknownCallModelImplementation(
                artifact.copy(sourceHash = "0".repeat(64)),
            ),
        )
        val changedIrFingerprint = fingerprint(
            implementation = TsEtsIrUnknownCallModelImplementation(
                artifact.copy(etsIrHash = "f".repeat(64)),
            ),
        )

        assertNotEquals(originalFingerprint, changedSourceFingerprint)
        assertNotEquals(originalFingerprint, changedIrFingerprint)
    }

    private fun fingerprint(implementation: TsEtsIrUnknownCallModelImplementation): String {
        val descriptor = TsUnknownCallModelDescriptor(
            id = "test.ets-ir.absolute",
            matcher = TsUnknownCallModelMatcher { true },
            supportedDomain = TsUnknownCallModelSupportedDomain(
                id = "number",
                description = "A resolved numeric argument",
            ),
            precision = TsUnknownCallModelPrecision.EXACT,
            implementationKind = TsUnknownCallModelImplementationKind.ETS_IR_BODY,
        )
        val registry = TsUnknownCallModelRegistry(
            registrations = listOf(
                TsUnknownCallModelRegistration(
                    descriptor = descriptor,
                    implementation = implementation,
                ),
            ),
            backends = listOf(TsEtsIrUnknownCallModelBackend),
        )

        return registry.freeze().fingerprint
    }
}
