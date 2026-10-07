package org.usvm.machine.call

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TsDateModelsArtifactTest {
    @Test
    fun `built in catalog registers Date family`() {
        val ids = TsBuiltInUnknownCallModels.catalog().modelIds

        assertTrue("ts.date.constructor" in ids, ids.toString())
        assertTrue("ts.date.getTime" in ids, ids.toString())
        assertTrue("ts.date.now" in ids, ids.toString())
    }

    @Test
    fun `native frontend loads Date source model family`() {
        val artifact = loadBundledEtsIrUnknownCallModelArtifact(
            resourceName = "/org/usvm/machine/call/models/DateModels.ts",
            sourceFileName = "DateModels.ts",
            entryPointClassName = "DateModels",
            entryPointMethodName = "construct",
        )
        val dateModels = artifact.file.allClasses.single { it.name == "DateModels" }

        assertEquals("construct", artifact.entryPoint.name)
        assertTrue(dateModels.methods.any { it.name == "getTime" })
        assertTrue(dateModels.methods.any { it.name == "toISOString" })
        assertTrue(dateModels.methods.all { method -> method.cfg.instructions.isNotEmpty() })
    }
}
