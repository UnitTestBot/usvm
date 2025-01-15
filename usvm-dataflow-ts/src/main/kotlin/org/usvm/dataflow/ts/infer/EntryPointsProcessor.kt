package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene

class EntryPointsProcessor(
    val scene: EtsScene,
) {
    fun extractEntryPoints(): ArtificialMainWithAllMethods {
        val artificialMainMethods = scene.projectClasses
            .asSequence()
            .flatMap { it.methods }
            .filter { it.name == "@dummyMain" }
            .toList()
        return ArtificialMainWithAllMethods(
            mainMethods = artificialMainMethods,
            allMethods = scene.projectClasses.flatMap { it.methods },
        )
    }
}

data class ArtificialMainWithAllMethods(
    val mainMethods: List<EtsMethod>,
    val allMethods: List<EtsMethod>,
)
