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
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsStringConstant
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.TestFactory
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.test.utils.loadEtsFileFromResource
import org.usvm.dataflow.ts.test.utils.testFactory
import org.usvm.dataflow.ts.util.EtsTraits
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

class EtsTestCasesTest {

    companion object {
        private fun load(path: String): EtsFile {
            return loadEtsFileFromResource("/$path")
        }
    }

    @TestFactory
    fun `type inference on testcases`() = testFactory {
        val name = "testcases"
        val file = load("ir/$name.ts.json")
        val project = EtsScene(listOf(file))
        val graph = createApplicationGraph(project)

        val allCases = project.classes.filter { it.name.startsWith("Case") }

        for (cls in allCases) {
            test(name = cls.name) {
                val entrypoint = cls.methods.single { it.name == "entrypoint" }
                logger.info { "Analyzing entrypoint: ${entrypoint.signature}" }

                val inferMethod = cls.methods.single { it.name == "infer" }
                val expectedTypeString = mutableMapOf<AccessPathBase, String>()
                var expectedReturnTypeString = ""
                for (inst in inferMethod.cfg.stmts) {
                    if (inst is EtsAssignStmt) {
                        val lhv = inst.lhv
                        if (lhv is EtsLocal) {
                            val rhv = inst.rhv
                            if (lhv.name == "EXPECTED_ARG_0") {
                                check(rhv is EtsStringConstant)
                                expectedTypeString[AccessPathBase.Arg(0)] = rhv.value
                                logger.info { "Expected type for arg 0: ${rhv.value}" }
                            }
                            if (lhv.name == "EXPECTED_ARG_1") {
                                check(rhv is EtsStringConstant)
                                expectedTypeString[AccessPathBase.Arg(1)] = rhv.value
                                logger.info { "Expected type for arg 1: ${rhv.value}" }
                            }
                            if (lhv.name == "EXPECTED_RETURN") {
                                check(rhv is EtsStringConstant)
                                expectedReturnTypeString = rhv.value
                                logger.info { "Expected return type: ${rhv.value}" }
                            }
                        }
                    }
                }
                val manager = with(EtsTraits) {
                    TypeInferenceManager(graph)
                }
                val result = manager.analyze(listOf(entrypoint))
                for (position in listOf(0, 1, 2).map { AccessPathBase.Arg(it) }) {
                    val expected = (expectedTypeString)[position]
                        ?: continue
                    val inferred = (result.inferredTypes[inferMethod]
                        ?: error("No inferred types for method ${inferMethod.enclosingClass.name}::${inferMethod.name}"))[position]
                    logger.info { "Inferred type for $position: $inferred" }
                    val passed = inferred.toString() == expected
                    assertTrue(
                        passed,
                        "Inferred type for $position does not match: inferred = $inferred, expected = $expected"
                    )
                }
                if (expectedReturnTypeString.isNotBlank()) {
                    val expected = expectedReturnTypeString
                    val inferred = result.inferredReturnType[inferMethod]
                        ?: error("No inferred return type for method ${inferMethod.enclosingClass.name}::${inferMethod.name}")
                    logger.info { "Inferred return type: $inferred" }
                    val passed = inferred.toString() == expected
                    assertTrue(
                        passed,
                        "Inferred return type does not match: inferred = $inferred, expected = $expected"
                    )
                }
            }
        }
    }

    private fun exportResultsToCSV(
        testResults: List<Triple<String, Boolean, Pair<String, String>>>,
        filePath: String,
    ) {
        val file = File(filePath)
        val writer = BufferedWriter(FileWriter(file, false))

        if (file.length() == 0L) {
            writer.write("Name,Status,Inferred,Expected\n")
        }

        testResults.forEach { (testName, passed, inferredExpected) ->
            val (inferred, expected) = inferredExpected
            writer.write("$testName,${if (passed) "Passed" else "Failed"},$inferred,$expected\n")
        }

        writer.close()
    }
}
