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

package org.usvm.dataflow.ts.test.utils

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsProjectFromIR
import org.usvm.dataflow.ts.infer.EntryPointsProcessor
import org.usvm.dataflow.ts.infer.TypeGuesser
import org.usvm.dataflow.ts.infer.TypeInferenceManager
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.annotation.annotateWithTypes
import org.usvm.dataflow.ts.infer.createApplicationGraph
import org.usvm.dataflow.ts.infer.verify.VerificationResult
import org.usvm.dataflow.ts.infer.verify.verify
import org.usvm.dataflow.ts.util.EtsTraits
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.test.assertTrue

object AbcProjects {
    private const val yourPrefixForTestFolders = "REPLACE_ME"
    private const val testProjectsVersion = "REPLACE_ME"
    private const val pathToSDK = "REPLACE_ME"

    @JvmStatic
    fun projectAvailable(): Boolean {
        return Path(yourPrefixForTestFolders).exists()
    }

    fun getAbcProject(abcPath: String): EtsScene {
        val projectAbc = "$yourPrefixForTestFolders/$testProjectsVersion/$abcPath"
        val abcScene = loadEtsProjectFromIR(Path(projectAbc), Path(pathToSDK))
        return abcScene
    }

    fun runOnAbcProject(scene: EtsScene): Pair<TypeInferenceResult, TypeInferenceStatistics> {
        val result = inferTypes(scene)
        val statistics = calculateStatistics(scene, result)
        return result to statistics
    }

    fun inferTypes(scene: EtsScene): TypeInferenceResult {
        val abcScene = when (val result = verify(scene)) {
            is VerificationResult.Success -> scene
            is VerificationResult.Fail -> scene.annotateWithTypes(result.erasureScheme)
        }

        assertTrue(verify(abcScene) is VerificationResult.Success)

        val graphAbc = createApplicationGraph(abcScene)
        val guesser = TypeGuesser(abcScene)

        val entrypoint = EntryPointsProcessor(abcScene).extractEntryPoints()
        val allMethods = entrypoint.allMethods.filter { it.isPublic }.filter { it.cfg.stmts.isNotEmpty() }

        val manager = TypeInferenceManager(EtsTraits(), graphAbc)
        val result = manager
            .analyze(entrypoint.mainMethods, allMethods, doAddKnownTypes = true)
            .withGuessedTypes(guesser)

        return result
    }

    fun calculateStatistics(scene: EtsScene, result: TypeInferenceResult): TypeInferenceStatistics {
        val graphAbc = createApplicationGraph(scene)
        val entrypoint = EntryPointsProcessor(scene).extractEntryPoints()
        val sceneStatistics = TypeInferenceStatistics()
        entrypoint.allMethods
            .filter { it.cfg.stmts.isNotEmpty() }
            .forEach {
                val methodTypeFacts = MethodTypesFacts.from(result, it)
                sceneStatistics.compareSingleMethodFactsWithTypesInScene(methodTypeFacts, it, graphAbc)
            }
        return sceneStatistics
    }
}
