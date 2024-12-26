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

package org.usvm.dataflow.ts.infer.annotation

import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsScene
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceResult

data class EtsTypeAnnotator(
    val scene: EtsScene,
    val typeInferenceResult: TypeInferenceResult,
) {
    private fun selectTypesFor(method: EtsMethod) = typeInferenceResult.inferredTypes[method] ?: emptyMap()

    private fun combinedThisFor(method: EtsMethod) = typeInferenceResult.inferredCombinedThisType[method.enclosingClass]

    fun annotateWithTypes(scene: EtsScene) = with(scene) {
        EtsScene(
            projectFiles = projectFiles.map { annotateWithTypes(it) }
        )
    }

    fun annotateWithTypes(file: EtsFile) = with(file) {
        EtsFile(
            signature = signature,
            classes = classes.map { annotateWithTypes(it) },
            namespaces = namespaces,
        )
    }

    fun annotateWithTypes(clazz: EtsClass) = with(clazz) {
        EtsClassImpl(
            signature = signature,
            fields = fields,
            methods = methods.map { annotateWithTypes(it) },
            ctor = annotateWithTypes(ctor),
            superClass = superClass, // TODO: replace with inferred superclass
            modifiers = modifiers,
            decorators = decorators,
            typeParameters = typeParameters,
        )
    }

    fun annotateWithTypes(method: EtsMethod) = with(method) {
        EtsMethodImpl(
            signature = signature,
            typeParameters = typeParameters,
            locals = locals,
            modifiers = modifiers,
            decorators = decorators,
        ).also {
            it._cfg = annotateWithTypes(cfg, selectTypesFor(this), combinedThisFor(this))
        }
    }

    fun annotateWithTypes(
        cfg: EtsCfg,
        types: Map<AccessPathBase, EtsTypeFact>,
        thisType: EtsTypeFact?,
    ) = with(cfg) {
        with(StmtTypeAnnotator(types, thisType, scene)) {
            EtsCfg(
                stmts = stmts.map { it.accept(this) },
                successorMap = stmts.associateWith { successors(it).toList() },
            )
        }
    }
}
