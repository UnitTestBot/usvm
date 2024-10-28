package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene

object EntryPointsProcessor {
    fun extractEntryPoints(
        scene: EtsScene,
    ): ArtificialMainWithAllMethods {
        val artificialMainMethods = scene.classes
            .asSequence()
            .flatMap { it.methods }
            .filter { it.name == "@dummyMain" }
            .toList()

        return ArtificialMainWithAllMethods(artificialMainMethods, scene.classes.flatMap { it.methods })
    }
}

data class ArtificialMainWithAllMethods(val mainMethods: List<EtsMethod>, val allMethods: List<EtsMethod>)
