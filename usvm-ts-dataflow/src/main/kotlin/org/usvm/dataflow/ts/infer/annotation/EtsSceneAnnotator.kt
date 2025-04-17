/*
 * Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package org.usvm.dataflow.ts.infer.annotation

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsThrowStmt

fun EtsScene.annotateWithTypes(scheme: TypeScheme): EtsScene =
    EtsSceneAnnotator(this, scheme).annotate()

class EtsSceneAnnotator(
    private val scene: EtsScene,
    private val scheme: TypeScheme,
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
            modifiers = modifiers,
            decorators = decorators,
        ).also { method ->
            val typeScheme = scheme.methodScheme(this)
            val thisType = scheme.thisType(this)
            val valueAnnotator = ValueTypeAnnotator(typeScheme, thisType)
            val exprAnnotator = ExprTypeAnnotator(scene, valueAnnotator)
            val stmtTypeAnnotator = StmtTypeAnnotator(valueAnnotator, exprAnnotator)
            val relocate = RelocateStmt(method)
            val relocated = cfg.stmts.associateWith { it.accept(stmtTypeAnnotator).accept(relocate) }
            val successorMap = cfg.stmts.map { stmt ->
                val newStmt = relocated[stmt] ?: error("Unprocessed stmt")
                newStmt to cfg.successors(stmt).map { relocated[it] ?: error("Unprocessed stmt") }.toList()
            }
            // TODO: method._cfg = EtsCfg(relocated.values.toList(), successorMap.toMap())
            TODO()
        }
    }

    private class RelocateStmt(val to: EtsMethod) : EtsStmt.Visitor<EtsStmt> {
        override fun visit(stmt: EtsNopStmt): EtsStmt =
            stmt.copy(location = stmt.location.copy(method = to))

        override fun visit(stmt: EtsAssignStmt): EtsStmt =
            stmt.copy(location = stmt.location.copy(method = to))

        override fun visit(stmt: EtsCallStmt): EtsStmt =
            stmt.copy(location = stmt.location.copy(method = to))

        override fun visit(stmt: EtsReturnStmt): EtsStmt =
            stmt.copy(location = stmt.location.copy(method = to))

        override fun visit(stmt: EtsThrowStmt): EtsStmt =
            stmt.copy(location = stmt.location.copy(method = to))

        override fun visit(stmt: EtsIfStmt): EtsStmt =
            stmt.copy(location = stmt.location.copy(method = to))

        override fun visit(stmt: EtsRawStmt): EtsStmt =
            stmt.copy(location = stmt.location.copy(method = to))
    }
}
