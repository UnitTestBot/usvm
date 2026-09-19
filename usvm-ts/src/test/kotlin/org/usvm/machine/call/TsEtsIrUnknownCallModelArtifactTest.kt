package org.usvm.machine.call

import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TsEtsIrUnknownCallModelArtifactTest {
    private val sourcePath = getResourcePath("/models/EtsIrSemanticModels.ts")

    @Test
    fun `native frontend loads a reusable model entry point`() {
        val artifact = loadEtsIrUnknownCallModelArtifact(
            sourcePath = sourcePath,
            entryPointClassName = "EtsIrSemanticModels",
            entryPointMethodName = "absolute",
        )

        val materialized = artifact.materializeFile()
        val materializedArtifact = artifact.materializeWith(materialized)

        assertEquals("absolute", artifact.entryPoint.name)
        assertEquals(artifact.entryPoint.signature, materializedArtifact.entryPoint.signature)
        assertTrue(materializedArtifact.entryPoint.cfg.instructions.isNotEmpty())
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
}
