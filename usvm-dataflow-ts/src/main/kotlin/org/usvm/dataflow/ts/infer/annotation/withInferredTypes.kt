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

package org.usvm.dataflow.ts.infer.annotation

import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.*
import org.usvm.dataflow.ts.infer.*

data class EtsTypeAnnotator(
    val scene: EtsScene,
    val typeInferenceResult: TypeInferenceResult,
) {
    private fun selectTypesFor(method: EtsMethod) = typeInferenceResult.inferredTypes[method] ?: emptyMap()

    private fun combinedThisFor(method: EtsMethod) = typeInferenceResult.inferredCombinedThisType[method.enclosingClass]
    
    fun annotateWithTypes(etsScene: EtsScene) = with(etsScene) {
        EtsScene(
            files = files.map { annotateWithTypes(it) }
        )
    }

    fun annotateWithTypes(etsFile: EtsFile) = with(etsFile) {
        EtsFile(
            signature = signature,
            classes = classes.map { annotateWithTypes(it) },
            namespaces = namespaces
        )
    }

    fun annotateWithTypes(etsClass: EtsClass) = with(etsClass) {
        EtsClassImpl(
            signature = signature,
            fields = fields,
            methods = methods.map { annotateWithTypes(it) },
            ctor = annotateWithTypes(ctor),
            superClass = superClass // TODO: replace with inferred superclass
        )
    }

    fun annotateWithTypes(method: EtsMethod) = with(method) {
        EtsModifiedMethod(
            signature = signature,
            locals = locals,
            cfg = annotateWithTypes(cfg, selectTypesFor(this), combinedThisFor(this)),
            modifiers = modifiers
        )
    }

    fun annotateWithTypes(cfg: EtsCfg, types: Map<AccessPathBase, EtsTypeFact>, thisType: EtsTypeFact?) = with(cfg) {
        with(StmtTypeAnnotator(types, thisType, scene)) {
            EtsCfg(
                stmts = stmts.map { it.accept(this) },
                successorMap = stmts.associateWith { successors(it).toList() }
            )
        }
    }
}
