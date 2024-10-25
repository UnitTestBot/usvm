package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene

fun runWithEntryPointsInformation(
    scene: EtsScene,
    runAnalysis: (Collection<EtsMethod>) -> TypeInferenceResult
): TypeInferenceResult {
    val allPublicMethods = scene.classes
        .asSequence()
        .flatMap { it.methods }
        .filter { it.isPublic }
        .toHashSet()

    val artificialMainMethods = scene.classes
        .asSequence()
        .flatMap { it.methods }
        .filter { it.name == "@dummyMain" }
        .toList()

    val analysisForEntryPoints = runAnalysis(artificialMainMethods)
    val methodsWithFacts = analysisForEntryPoints.inferredTypes.keys

    val remainingMethods = allPublicMethods - methodsWithFacts
    val remainingAnalysis = runAnalysis(remainingMethods)

    val combinedResults = analysisForEntryPoints.merge(remainingAnalysis)

    return combinedResults
}
