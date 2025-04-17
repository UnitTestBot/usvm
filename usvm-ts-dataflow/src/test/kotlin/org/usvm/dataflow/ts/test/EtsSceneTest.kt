/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.usvm.dataflow.ts.test

import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFieldImpl
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsVoidType
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.dataflow.ts.graph.EtsApplicationGraphImpl
import kotlin.test.Test
import kotlin.test.assertEquals

class EtsSceneTest {

    @Test
    fun `test create EtsScene with multiple files`() {
        val fileCatSignature = EtsFileSignature(
            projectName = "TestProject",
            fileName = "cat.ts",
        )
        val classCatSignature = EtsClassSignature(
            name = "Cat",
            file = fileCatSignature,
            namespace = null,
        )
        val fieldName = EtsFieldImpl(
            signature = EtsFieldSignature(
                enclosingClass = classCatSignature,
                name = "name",
                type = EtsStringType,
            )
        )
        val methodMeow = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classCatSignature,
                name = "meow",
                parameters = emptyList(),
                returnType = EtsVoidType,
            )
        )
        val ctorCat = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classCatSignature,
                name = CONSTRUCTOR_NAME,
                parameters = emptyList(),
                returnType = EtsVoidType,
            ),
        )
        val classCat = EtsClassImpl(
            signature = classCatSignature,
            fields = listOf(fieldName),
            methods = listOf(ctorCat, methodMeow),
        )
        val fileCat = EtsFile(
            signature = fileCatSignature,
            classes = listOf(classCat),
            namespaces = emptyList(),
        )

        val fileBoxSignature = EtsFileSignature(
            projectName = "TestProject",
            fileName = "box.ts",
        )
        val classBoxSignature = EtsClassSignature(
            name = "Box",
            file = fileBoxSignature,
            namespace = null,
        )
        val fieldCats = EtsFieldImpl(
            signature = EtsFieldSignature(
                enclosingClass = classBoxSignature,
                name = "cats",
                type = EtsArrayType(EtsClassType(classCatSignature), 1),
            )
        )
        val methodTouch = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classBoxSignature,
                name = "touch",
                parameters = emptyList(),
                returnType = EtsVoidType,
            )
        ).also {
            var index = 0
            val stmts = listOf(
                EtsAssignStmt(
                    location = EtsStmtLocation(it, index++),
                    lhv = EtsLocal("this", EtsClassType(classBoxSignature)),
                    rhv = EtsThis(EtsClassType(classBoxSignature)),
                ),
                EtsCallStmt(
                    location = EtsStmtLocation(it, index++),
                    expr = EtsInstanceCallExpr(
                        instance = EtsLocal("this", EtsClassType(classBoxSignature)),
                        callee = methodMeow.signature,
                        args = emptyList(),
                        type = methodMeow.signature.returnType,
                    )
                ),
                EtsReturnStmt(
                    location = EtsStmtLocation(it, index++),
                    returnValue = null,
                )
            )
            check(index == stmts.size)
            val blocks = listOf(
                BasicBlock(
                    id = 0,
                    statements = stmts,
                ),
            )
            val successors: Map<Int, List<Int>> = mapOf(
                0 to listOf(),
            )
            val cfg = EtsBlockCfg(
                blocks = blocks,
                successors = successors,
            )
            it._cfg = cfg
        }
        val ctorBox = EtsMethodImpl(
            signature = EtsMethodSignature(
                enclosingClass = classBoxSignature,
                name = CONSTRUCTOR_NAME,
                parameters = emptyList(),
                returnType = EtsVoidType,
            ),
        )
        val classBox = EtsClassImpl(
            signature = classBoxSignature,
            fields = listOf(fieldCats),
            methods = listOf(ctorBox, methodTouch),
        )
        val fileBox = EtsFile(
            signature = fileBoxSignature,
            classes = listOf(classBox),
            namespaces = emptyList(),
        )

        val project = EtsScene(listOf(fileCat, fileBox))
        val graph = EtsApplicationGraphImpl(project)

        val callStmt = project.projectClasses
            .asSequence()
            .flatMap { it.methods }
            .filter { it.name == "touch" }
            .flatMap { it.cfg.stmts }
            .filterIsInstance<EtsCallStmt>()
            .first()
        assertEquals(methodMeow, graph.callees(callStmt).first())
    }
}
