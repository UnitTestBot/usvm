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
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KotlinLogging
import org.jacodb.ets.base.ANONYMOUS_CLASS_PREFIX
import org.jacodb.ets.base.ANONYMOUS_METHOD_PREFIX
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectFromMultipleIR
import org.usvm.dataflow.ts.infer.EntryPointsProcessor
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.infer.dto.toDto
import org.usvm.dataflow.ts.util.EtsTraits
import java.nio.file.Path
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

    val input by option("-i", "--input", help = "Input file or directory with IR").path().multiple(required = true)
    val output by option("-o", "--output", help = "Output file with inferred types in JSON format").path().required()

    val sdkPaths by option(
        "--sdk",
        help = "Path to SDK directory"
    ).path().multiple()

    val sdkNames by option(
        "--sdk-names",
        help = "Comma-separated list of SDK names"
    ).convert { it.split(",") }.default(emptyList(), defaultForHelp = "empty")

    val skipAnonymous by option(
        "--skip-anonymous",
        help = "Skip anonymous classes and method"
    ).flag("--no-skip-anonymous", default = false)

    override fun run() {
        logger.info { "Running InferTypes" }
        val startTime = System.currentTimeMillis()

        logger.info { "Input: $input" }
        logger.info { "Output: $output" }

        val project = loadEtsProjectFromMultipleIR(input, sdkPaths)
        val graph = createApplicationGraph(project)

        val (dummyMains, allMethods) = EntryPointsProcessor.extractEntryPoints(project)
        val publicMethods = allMethods.filter { m ->
            m.isPublic && m.signature.enclosingClass.file.projectName !in sdkNames
        }

        val manager = TypeInferenceManager(EtsTraits(), graph)

        val (result, timeAnalyze) = measureTimedValue {
            manager.analyze(dummyMains, publicMethods).withGuessedTypes(project)
        }
        logger.info { "Inferred types for ${result.inferredTypes.size} methods in $timeAnalyze" }

        dumpTypeInferenceResult(result, output, skipAnonymous)

        logger.info { "All done in %.1f s".format((System.currentTimeMillis() - startTime) / 1000.0) }
    }
}

fun main(args: Array<String>) {
    InferTypes().main(args)
}

@OptIn(ExperimentalSerializationApi::class)
fun dumpTypeInferenceResult(
    result: TypeInferenceResult,
    path: Path,
    skipAnonymous: Boolean = true,
) {
    logger.info { "Dumping inferred types to '$path'" }
    val dto = result.toDto()
        // Filter out anonymous classes and methods
        .let { dto ->
            if (skipAnonymous) {
                dto.copy(
                    classes = dto.classes.filterNot { cls ->
                        cls.signature.name.startsWith(ANONYMOUS_CLASS_PREFIX)
                    },
                    methods = dto.methods.filterNot { method ->
                        method.signature.declaringClass.name.startsWith(ANONYMOUS_CLASS_PREFIX)
                            || method.signature.name.startsWith(ANONYMOUS_METHOD_PREFIX)
                    }
                )
            } else {
                dto
            }
        }
    path.outputStream().use { stream ->
        val json = Json {
            prettyPrint = true
        }
        json.encodeToStream(dto, stream)
    }
}
