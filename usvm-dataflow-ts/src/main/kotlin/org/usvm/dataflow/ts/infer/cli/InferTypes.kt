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
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KotlinLogging
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.test.utils.loadEtsFile
import org.jacodb.ets.test.utils.loadMultipleEtsFilesFromDirectory
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.infer.dto.toDto
import org.usvm.dataflow.ts.util.EtsTraits
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream
import kotlin.time.measureTimedValue

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

    val input by option("-i", "--input", help = "Input file").path().multiple(required = true)
    val output by option("-o", "--output", help = "Output file").path().required()

    override fun run() {
        logger.info { "Running InferTypes" }
        val startTime = System.currentTimeMillis()

        logger.info { "Input: $input" }
        logger.info { "Output: $output" }

        val project = loadEtsScene(input)
        val graph = createApplicationGraph(project)
        val entrypoints = project.classes.asSequence().flatMap { it.methods }
            .filter { it.isPublic }.toList()
        val manager = with(EtsTraits) {
            TypeInferenceManager(graph)
        }
        val (result, timeAnalyze) = measureTimedValue {
            manager.analyze(entrypoints)
        }
        logger.info { "Inferred types for ${result.inferredTypes.size} methods in $timeAnalyze" }

        dumpTypeInferenceResult(result, output)

        logger.info { "All done in %.1f s".format((System.currentTimeMillis() - startTime) / 1000.0) }
    }
}

fun main(args: Array<String>) {
    InferTypes().main(args)
}

private fun loadEtsScene(paths: List<Path>): EtsScene {
    logger.info { "Loading ETS scene from $paths" }
    val files = paths.flatMap {  path->
        if (path.isRegularFile()) {
            logger.info { "Loading single ETS file: $path" }
            val file = loadEtsFile(path)
            listOf(file)
        } else {
            logger.info { "Loading multiple ETS files: $path/**" }
            loadMultipleEtsFilesFromDirectory(path).asIterable()
        }
    }
    return EtsScene(files)
}

@OptIn(ExperimentalSerializationApi::class)
fun dumpTypeInferenceResult(result: TypeInferenceResult, path: Path) {
    logger.info { "Dumping inferred types to '$path'" }
    val dto = result.toDto()
    path.outputStream().use { stream ->
        val json = Json {
            prettyPrint = true
        }
        json.encodeToStream(dto, stream)
    }
}
