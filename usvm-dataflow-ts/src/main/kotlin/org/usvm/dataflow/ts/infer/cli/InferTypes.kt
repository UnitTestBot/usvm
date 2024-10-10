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

@file:Suppress("MemberVisibilityCanBePrivate")

package org.usvm.dataflow.ts.infer.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import mu.KotlinLogging
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.infer.dto.TypeInferenceResultDto
import org.usvm.dataflow.ts.infer.dto.toDto
import org.usvm.dataflow.ts.util.EtsTraits
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

private val logger = KotlinLogging.logger {}

class InferTypes : CliktCommand() {
    init {
        context {
            helpFormatter = {
                MordantHelpFormatter(
                    it,
                    requiredOptionMarker = "*",
                    showDefaultValues = true,
                    showRequiredTag = true
                )
            }
        }
    }

    val input by option("-i", "--input", help = "Input file").path().required()
    val output by option("-o", "--output", help = "Output file").path().required()

    override fun run() {
        logger.info { "Running InferTypes" }
        val startTime = System.currentTimeMillis()

        logger.info { "Input file: $input" }
        logger.info { "Output file: $output" }

        val project = loadEtsScene(input)
        val graph = createApplicationGraph(project)
        val entrypoints = project.classes
            .asSequence()
            .flatMap { it.methods }
            // .filter { it.name == "build" }
            .filter { it.isPublic }
            .toList()
        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }
        val result = manager.analyze(entrypoints)

        logger.info { "Inferred types for ${result.inferredTypes.size} methods" }

        logger.info { "All done in %.1s".format((System.currentTimeMillis() - startTime) / 1000.0) }

        dumpEtsTypeInferenceResult(result.toDto(), output)
    }
}

fun main(args: Array<String>) {
    InferTypes().main(args)
}

@OptIn(ExperimentalSerializationApi::class)
private fun loadEtsScene(path: Path): EtsScene {
    return path.inputStream().use { stream ->
        val files = Json.decodeFromStream<List<EtsFile>>(stream)
        EtsScene(files)
    }
}

private val json = Json { prettyPrint = true }

@OptIn(ExperimentalSerializationApi::class)
fun dumpEtsTypeInferenceResult(resultDto: TypeInferenceResultDto, path: Path) {
    path.outputStream().use { stream ->
        json.encodeToStream(resultDto, stream)
    }
}
