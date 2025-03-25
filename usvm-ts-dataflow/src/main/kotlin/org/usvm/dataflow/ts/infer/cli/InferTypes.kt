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
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import mu.KotlinLogging
import org.jacodb.ets.utils.loadEtsProjectFromMultipleIR
import org.usvm.dataflow.ts.infer.EntryPointsProcessor
import org.usvm.dataflow.ts.infer.TypeGuesser
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.util.EtsTraits
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

    val skipAnonymous by option(
        "--skip-anonymous",
        help = "Skip anonymous classes and method"
    ).flag("--no-skip-anonymous", default = false)

    val useKnownTypes by option(
        "--use-known-types",
        help = "Do take into account the known types in scene"
    ).flag("--no-use-known-types", default = true)

    val enableAliasAnalysis by option(
        "--alias-analysis",
        help = "Enable alias analysis"
    ).flag("--no-alias-analysis", default = true)

    override fun run() {
        logger.info { "Running InferTypes" }
        val startTime = System.currentTimeMillis()

        logger.info { "Input: $input" }
        logger.info { "Output: $output" }

        val project = loadEtsProjectFromMultipleIR(input, sdkPaths)
        val graph = createApplicationGraph(project)
        val guesser = TypeGuesser(project)

        val (dummyMains, allMethods) = EntryPointsProcessor(project).extractEntryPoints()
        val publicMethods = allMethods.filter { m -> m.isPublic }

        val manager = TypeInferenceManager(EtsTraits(), graph)

        val (resultBasic, timeAnalyze) = measureTimedValue {
            manager.analyze(
                entrypoints = dummyMains,
                allMethods = publicMethods,
                doAddKnownTypes = useKnownTypes,
                doAliasAnalysis = enableAliasAnalysis,
            )
        }
        logger.info { "Inferred types for ${resultBasic.inferredTypes.size} methods in $timeAnalyze" }

        val (result, timeGuess) = measureTimedValue {
            resultBasic.withGuessedTypes(guesser)
        }
        logger.info { "Guessed types for ${result.inferredTypes.size} methods in $timeGuess" }

        dumpTypeInferenceResult(result, output, skipAnonymous)

        logger.info { "All done in %.1f s".format((System.currentTimeMillis() - startTime) / 1000.0) }
    }
}

fun main(args: Array<String>) {
    InferTypes().main(args)
}
