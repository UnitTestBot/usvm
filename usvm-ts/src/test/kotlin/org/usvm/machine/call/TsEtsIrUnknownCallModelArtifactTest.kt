package org.usvm.machine.call

import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.usvm.util.getResourcePath
import kotlin.io.path.copyTo
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `loader rejects source changed while EtsIR is generated`() {
        val mutableSourcePath = createTempFile(prefix = "EtsIrSemanticModels", suffix = ".ts")
        sourcePath.copyTo(mutableSourcePath, overwrite = true)

        try {
            val error = assertFailsWith<IllegalStateException> {
                loadEtsIrUnknownCallModelArtifact(
                    sourcePath = mutableSourcePath,
                    entryPointClassName = "EtsIrSemanticModels",
                    entryPointMethodName = "absolute",
                    generateIr = { path ->
                        val irPath = generateEtsIR(
                            projectPath = path,
                            isProject = false,
                            loadEntrypoints = true,
                            useArkAnalyzerTypeInference = null,
                            provider = EtsIrProvider.TS_FRONTEND,
                        )
                        path.writeBytes(path.readBytes() + byteArrayOf('\n'.code.toByte()))
                        irPath
                    },
                )
            }

            assertTrue(error.message.orEmpty().contains("changed while generating EtsIR"))
        } finally {
            mutableSourcePath.deleteIfExists()
        }
    }

    @Test
    fun `loader rejects instance entry points`() {
        val error = assertFailsWith<IllegalStateException> {
            loadEtsIrUnknownCallModelArtifact(
                sourcePath = sourcePath,
                entryPointClassName = "EtsIrSemanticModels",
                entryPointMethodName = "instanceIdentity",
            )
        }

        assertTrue(error.message.orEmpty().contains("must be static"))
    }

    @Test
    fun `loader rejects declaration-only entry points`() {
        val error = assertFailsWith<IllegalStateException> {
            loadEtsIrUnknownCallModelArtifact(
                sourcePath = getResourcePath("/models/EtsIrSemanticModelCalls.ts"),
                entryPointClassName = "ExternalModels",
                entryPointMethodName = "absolute",
            )
        }

        assertTrue(error.message.orEmpty().contains("must have a body"))
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
