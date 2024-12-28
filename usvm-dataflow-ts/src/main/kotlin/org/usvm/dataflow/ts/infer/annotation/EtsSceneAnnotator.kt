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
import org.usvm.dataflow.ts.infer.TypeInferenceResult

fun EtsScene.annotateWithTypes(result: TypeInferenceResult): EtsScene =
    EtsSceneAnnotator(this, result).annotate()

class EtsSceneAnnotator(
    private val scene: EtsScene,
    private val result: TypeInferenceResult,
) {
    fun annotate(): EtsScene = scene.annotate()

    private fun EtsScene.annotate(): EtsScene {
        return EtsScene(
            projectFiles = projectFiles.map { it.annotateWithTypes() },
            sdkFiles = sdkFiles.map { it.annotateWithTypes() },
        )
    }

    private fun EtsFile.annotateWithTypes(): EtsFile {
        return EtsFile(
            signature = signature,
            classes = classes.map { it.annotateWithTypes() },
            namespaces = namespaces,
        )
    }

    private fun EtsClass.annotateWithTypes(): EtsClass {
        return EtsClassImpl(
            signature = signature,
            fields = fields,
            methods = methods.map { it.annotateWithTypes() },
            ctor = ctor.annotateWithTypes(),
            superClass = superClass, // TODO: replace with inferred superclass
            typeParameters = typeParameters,
            modifiers = modifiers,
            decorators = decorators,
        )
    }

    private fun EtsMethod.annotateWithTypes(): EtsMethod {
        return EtsMethodImpl(
            signature = signature,
            typeParameters = typeParameters,
            locals = locals,
            modifiers = modifiers,
            decorators = decorators,
        ).also { method ->
            val types = result.inferredTypes[this].orEmpty()
            val thisType = result.inferredCombinedThisType[enclosingClass]
            val valueAnnotator = ValueTypeAnnotator(scene, types, thisType)
            val exprAnnotator = ExprTypeAnnotator(scene, valueAnnotator)
            val stmtTypeAnnotator = StmtTypeAnnotator(valueAnnotator, exprAnnotator)
            method._cfg = EtsCfg(
                stmts = cfg.stmts.map { it.accept(stmtTypeAnnotator) },
                successorMap = cfg.stmts.associateWith { cfg.successors(it).toList() },
            )
        }
    }
}
