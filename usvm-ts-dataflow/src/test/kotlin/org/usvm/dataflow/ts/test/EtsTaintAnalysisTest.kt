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
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.ConstantTrue
import org.jacodb.taint.configuration.ContainsMark
import org.jacodb.taint.configuration.CopyAllMarks
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.dataflow.ifds.SingletonUnit
import org.usvm.dataflow.ifds.UnitResolver
import org.usvm.dataflow.taint.TaintManager
import org.usvm.dataflow.ts.graph.EtsApplicationGraphImpl
import org.usvm.dataflow.ts.loadEtsFileFromResource
import org.usvm.dataflow.ts.util.EtsTraits
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

@Disabled("Need to update IR")
class EtsTaintAnalysisTest {

    companion object {
        private const val BASE_PATH = "/samples/etsir/ast"
        private const val DECOMPILED_PATH = "/decompiled"

        private fun loadFromProject(name: String): EtsFile {
            return loadEtsFileFromResource("$BASE_PATH/$name.ts.json")
        }

        private fun loadDecompiled(name: String): EtsFile {
            return loadEtsFileFromResource("$DECOMPILED_PATH/$name.abc.json")
        }

        val getConfigForMethod: (EtsMethod) -> List<TaintConfigurationItem>? =
            { method ->
                val rules = buildList {
                    if (method.name == "source") add(
                        TaintMethodSource(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                AssignMark(mark = TaintMark("TAINT"), position = Result),
                            ),
                        )
                    )
                    if (method.name == "sink") add(
                        TaintMethodSink(
                            method = method,
                            ruleNote = "SINK", // FIXME
                            cwe = listOf(), // FIXME
                            condition = ContainsMark(position = Argument(0), mark = TaintMark("TAINT"))
                        )
                    )
                    if (method.name == "pass") add(
                        TaintPassThrough(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                CopyAllMarks(from = Argument(0), to = Result)
                            ),
                        )
                    )
                    if (method.name == "validate") add(
                        TaintPassThrough(
                            method = method,
                            condition = ConstantTrue,
                            actionsAfter = listOf(
                                RemoveMark(mark = TaintMark("TAINT"), position = Argument(0))
                            ),
                        )
                    )
                }
                rules.ifEmpty { null }
            }
    }

    fun runTaintAnalysis(project: EtsScene) {
        val graph = EtsApplicationGraphImpl(project)
        val unitResolver = UnitResolver<EtsMethod> { SingletonUnit }

        val manager = TaintManager(
            traits = EtsTraits(),
            graph = graph,
            unitResolver = unitResolver,
            getConfigForMethod = getConfigForMethod,
        )

        val methods = project.projectClasses.flatMap { it.methods }.filter { it.name == "bad" }
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.name}" }
        }
        val sinks = manager.analyze(methods, timeout = 60.seconds)
        logger.info { "Sinks: $sinks" }
        assertTrue(sinks.isNotEmpty())
    }

    @Test
    fun `test taint analysis`() {
        val file = loadFromProject("TaintAnalysis")
        val project = EtsScene(listOf(file))
        runTaintAnalysis(project)
    }

    @Disabled("Need to update the EtsIR-ABC json file")
    @Test
    fun `test taint analysis on decompiled file`() {
        val file = loadDecompiled("TaintAnalysis")
        val project = EtsScene(listOf(file))
        runTaintAnalysis(project)
    }
}
