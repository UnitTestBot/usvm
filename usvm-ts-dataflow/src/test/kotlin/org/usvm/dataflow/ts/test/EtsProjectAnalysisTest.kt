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

package org.usvm.dataflow.ts.test

import mu.KotlinLogging
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.usvm.dataflow.ifds.SingletonUnit
import org.usvm.dataflow.ifds.UnitResolver
import org.usvm.dataflow.taint.TaintManager
import org.usvm.dataflow.taint.TaintVulnerability
import org.usvm.dataflow.ts.getResourcePath
import org.usvm.dataflow.ts.getResourceStream
import org.usvm.dataflow.ts.graph.EtsApplicationGraphImpl
import org.usvm.dataflow.ts.loadEtsFileFromResource
import org.usvm.dataflow.ts.test.utils.getConfigForMethod
import org.usvm.dataflow.ts.test.utils.loadRules
import org.usvm.dataflow.ts.util.EtsTraits
import org.usvm.dataflow.util.getPathEdges
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.toPath
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class EtsProjectAnalysisTest {
    private var tsLinesSuccess = 0L
    private var tsLinesFailed = 0L
    private var analysisTime: Duration = Duration.ZERO
    private var totalPathEdges = 0
    private var totalSinks: MutableList<TaintVulnerability<EtsStmt>> = mutableListOf()

    companion object {
        private const val SOURCE_PROJECT_PATH =
            "/projects/applications_app_samples/source/applications_app_samples/code/SuperFeature/DistributedAppDev/ArkTSDistributedCalc"
        private const val PROJECT_PATH = "/projects/applications_app_samples/etsir/ast/ArkTSDistributedCalc"
        private const val START_PATH = "/entry/src/main/ets"
        private const val BASE_PATH = PROJECT_PATH
        private const val SOURCE_BASE_PATH = SOURCE_PROJECT_PATH + START_PATH

        private fun loadFromProject(filename: String): EtsFile {
            return loadEtsFileFromResource("$BASE_PATH/$filename.json")
        }

        private fun countFileLines(path: String): Long {
            return getResourceStream(path).bufferedReader().use { reader ->
                reader.lines().count()
            }
        }

        val rules by lazy { loadRules("config1.json") }
    }

    private fun projectAvailable(): Boolean {
        val path = object {}::class.java.getResource(PROJECT_PATH)?.toURI()?.toPath()
        return path != null && path.exists()
    }

    @EnabledIf("projectAvailable")
    @Test
    fun processAllFiles() {
        val baseDir = getResourcePath(BASE_PATH)
        Files.walk(baseDir)
            .filter { it.toString().endsWith(".json") }
            .map { baseDir.relativize(it).toString().replace("\\", "/").substringBeforeLast('.') }
            .forEach { filename ->
                handleFile(filename)
            }
        makeReport()
    }

    private fun makeReport() {
        logger.info { "Analysis Report On $PROJECT_PATH" }
        logger.info { "====================" }
        logger.info { "Total files processed: ${tsLinesSuccess + tsLinesFailed}" }
        logger.info { "Successfully processed lines: $tsLinesSuccess" }
        logger.info { "Failed lines: $tsLinesFailed" }
        logger.info { "Total analysis time: $analysisTime" }
        logger.info { "Total path edges: $totalPathEdges" }
        logger.info { "Found sinks: ${totalSinks.size}" }

        if (totalSinks.isNotEmpty()) {
            totalSinks.forEachIndexed { idx, sink ->
                logger.info {
                    """Detailed Sink Information:
                |
                |Sink ID: $idx
                |Statement: ${sink.sink.statement}
                |Fact: ${sink.sink.fact}
                |Condition: ${sink.rule?.condition}
                |
                """.trimMargin()
                }
            }
        } else {
            logger.info { "No sinks found." }
        }
        logger.info { "====================" }
        logger.info { "End of report" }
    }

    private fun handleFile(filename: String) {
        val fileLines = countFileLines("$SOURCE_BASE_PATH/$filename")
        try {
            logger.info { "Processing '$filename'" }
            val file = loadFromProject(filename)
            val project = EtsScene(listOf(file), sdkFiles = emptyList())
            val startTime = System.currentTimeMillis()
            runAnalysis(project)
            val endTime = System.currentTimeMillis()
            analysisTime += (endTime - startTime).milliseconds
            tsLinesSuccess += fileLines
        } catch (e: Exception) {
            logger.warn { "Failed to process '$filename': $e" }
            logger.warn { e.stackTraceToString() }
            tsLinesFailed += fileLines
        }
    }

    private fun runAnalysis(project: EtsScene) {
        val graph = EtsApplicationGraphImpl(project)
        val unitResolver = UnitResolver<EtsMethod> { SingletonUnit }
        val manager = TaintManager(
            traits = EtsTraits(),
            graph = graph,
            unitResolver = unitResolver,
            getConfigForMethod = { method -> getConfigForMethod(method, rules) },
        )
        val methods = project.projectClasses.flatMap { it.methods }
        val sinks = manager.analyze(methods, timeout = 10.seconds)
        totalPathEdges += manager.runnerForUnit.values.sumOf { it.getPathEdges().size }
        totalSinks += sinks
    }
}
