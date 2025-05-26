/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.usvm.ts.service

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.jacodb.ets.dto.SceneDto
import org.jacodb.ets.dto.toEtsScene
import org.usvm.dataflow.ts.infer.EntryPointsProcessor
import org.usvm.dataflow.ts.infer.TypeGuesser
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.infer.proto.toProto
import org.usvm.dataflow.ts.infer.proto.ProtoInferredTypes
import org.usvm.dataflow.ts.util.EtsTraits
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger {}

@Serializable
data class InferTypesRequestDto(
    val scene: SceneDto? = null,
    val path: String? = null,
)

fun handleInfer(request: InferTypesRequestDto): ProtoInferredTypes {
    val startTime = System.currentTimeMillis()

    val sceneDto = request.scene ?: run {
        logger.info { "Scene is null, requesting scene from ArkAnalyzer" }
        val path = checkNotNull(request.path)
        val port = 50051
        //
        logger.info { "call GetScene(path = \"$path\")" }
        TODO()
    }
    val scene = sceneDto.toEtsScene()
    val graph = createApplicationGraph(scene)
    val guesser = TypeGuesser(scene)

    val (dummyMains, allMethods) = EntryPointsProcessor(scene).extractEntryPoints()
    val publicMethods = allMethods.filter { m -> m.isPublic }

    val manager = TypeInferenceManager(EtsTraits(), graph)

    val useKnownTypes = true
    val enableAliasAnalysis = true

    val (resultBasic, timeAnalyze) = measureTimedValue {
        manager.analyze(
            entrypoints = dummyMains,
            allMethods = publicMethods,
            doAddKnownTypes = useKnownTypes,
            doAliasAnalysis = enableAliasAnalysis,
        )
    }
    logger.info {
        "Inferred types for ${resultBasic.inferredTypes.size} methods in %.1fs"
            .format(timeAnalyze.toDouble(DurationUnit.SECONDS))
    }

    val (result, timeGuess) = measureTimedValue {
        resultBasic.withGuessedTypes(guesser)
    }
    logger.info {
        "Guessed types for ${result.inferredTypes.size} methods in %.1fs"
            .format(timeGuess.toDouble(DurationUnit.SECONDS))
    }
    logger.info {
        "Done type inference in %.1f s"
            .format((System.currentTimeMillis() - startTime) / 1000.0)
    }

    logger.info { "Converting to proto..." }
    val (resultProto, timeProto) = measureTimedValue {
        result.toProto()
    }
    logger.info{
        "Converted to proto in %.1f s"
            .format(timeProto.toDouble(DurationUnit.SECONDS))
    }

    logger.info {
        "All done in %.1f s"
            .format((System.currentTimeMillis() - startTime) / 1000.0)
    }
    return resultProto
}
