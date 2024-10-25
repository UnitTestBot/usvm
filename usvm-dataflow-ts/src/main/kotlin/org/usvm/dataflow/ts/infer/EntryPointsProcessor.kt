package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene

object EntryPointsProcessor {
    fun extractEntryPoints(
        scene: EtsScene,
    ): Pair<List<EtsMethod>, List<EtsMethod>> { // TODO introduce a type for it
        val artificialMainMethods = scene.classes
            .asSequence()
            .flatMap { it.methods }
            .filter { it.name == "@dummyMain" }
            .toList()

        return artificialMainMethods to scene.classes.flatMap { it.methods }
    }
}
