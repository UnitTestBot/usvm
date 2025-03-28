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

import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsDecorator
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsModifiers
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.annotation.InferredTypeScheme
import org.usvm.dataflow.ts.infer.annotation.annotateWithTypes
import org.usvm.dataflow.ts.infer.verify.VerificationResult
import org.usvm.dataflow.ts.infer.verify.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EtsTypeAnnotationTest {

    @Test
    fun `test EtsTypeAnnotator`() {
        val typeInferenceResult = TypeInferenceResult(
            inferredTypes = mapOf(
                mainMethod to mapOf(
                    AccessPathBase.Arg(1) to EtsTypeFact.StringEtsTypeFact,
                    AccessPathBase.Arg(2) to EtsTypeFact.NumberEtsTypeFact,
                    AccessPathBase.Local("v1") to EtsTypeFact.StringEtsTypeFact,
                    AccessPathBase.Local("v2") to EtsTypeFact.NumberEtsTypeFact,
                    AccessPathBase.Local("v3") to EtsTypeFact.StringEtsTypeFact,
                )
            ),
            inferredReturnType = mapOf(
                mainMethod to EtsTypeFact.StringEtsTypeFact,
            ),
            inferredCombinedThisType = mapOf(
                mainClassSignature to EtsTypeFact.ObjectEtsTypeFact(
                    cls = EtsClassType(mainClassSignature),
                    properties = mapOf(),
                )
            )
        )

        val annotatedScene = sampleScene.annotateWithTypes(InferredTypeScheme(typeInferenceResult))

        val verificationResult = verify(annotatedScene)
        assertIs<VerificationResult.Success>(verificationResult)

        with(verificationResult) {
            val methodScheme = scheme.methodSchemes().single()

            assertEquals(EtsStringType, methodScheme.typeOf(AccessPathBase.Arg(1)))
            assertEquals(EtsNumberType, methodScheme.typeOf(AccessPathBase.Arg(2)))

            assertEquals(EtsStringType, methodScheme.typeOf(AccessPathBase.Local("v1")))
            assertEquals(EtsNumberType, methodScheme.typeOf(AccessPathBase.Local("v2")))
            assertEquals(EtsStringType, methodScheme.typeOf(AccessPathBase.Local("v3")))
        }
    }

    private val mainTs = EtsFileSignature(
        projectName = "sampleProject",
        fileName = "main.ts",
    )

    private val mainClassSignature = EtsClassSignature(
        name = "MainClass",
        file = mainTs,
        namespace = null,
    )

    private val mainMethodSignature = EtsMethodSignature(
        enclosingClass = mainClassSignature,
        name = "mainMethod",
        parameters = parameters(2),
        returnType = EtsUnknownType,
    )

    private val mainMethod = buildMethod(mainMethodSignature) {
        val arg1 = EtsParameterRef(1)
        val arg2 = EtsParameterRef(2)
        val v1 = EtsLocal("v1", EtsUnknownType)
        val v2 = EtsLocal("v2", EtsUnknownType)
        val v3 = EtsLocal("v3", EtsUnknownType)

        push(EtsAssignStmt(location, v1, arg1))
        push(EtsAssignStmt(location, v2, arg2))
        push(EtsAssignStmt(location, v3, EtsAddExpr(v1, v2)))
        push(EtsReturnStmt(location, v3))
    }

    private val mainClassCtorSignature = EtsMethodSignature(
        enclosingClass = mainClassSignature,
        name = CONSTRUCTOR_NAME,
        parameters = parameters(1),
        returnType = EtsUnknownType,
    )

    private val mainClassCtor = buildMethod(mainClassCtorSignature) {
        val local = EtsLocal("this", EtsClassType(mainClassSignature))
        val etsThis = EtsThis
        push(EtsAssignStmt(location, local, etsThis))
        push(EtsReturnStmt(location, local))
    }

    private val mainClass = EtsClassImpl(
        signature = mainClassSignature,
        fields = listOf(),
        methods = listOf(mainClassCtor, mainMethod),
        superClass = null,
    )

    private val mainFile = EtsFile(
        signature = mainTs,
        classes = listOf(mainClass),
        namespaces = listOf(),
    )

    private val sampleScene = EtsScene(listOf(mainFile), sdkFiles = emptyList())

    private class CfgBuilderContext(
        val method: EtsMethod,
    ) {
        private val stmts = mutableListOf<EtsStmt>()
        private val successorsMap = mutableMapOf<EtsStmt, MutableList<EtsStmt>>()

        fun build(): EtsBlockCfg = TODO()

        fun push(stmt: EtsStmt) {
            stmts.lastOrNull()?.let { link(it, stmt) }
            successorsMap[stmt] = mutableListOf()
            stmts.add(stmt)
        }

        fun link(from: EtsStmt, to: EtsStmt) {
            successorsMap.getOrPut(from, ::mutableListOf).add(to)
        }

        val location: EtsStmtLocation
            get() = EtsStmtLocation(method, stmts.size)
    }

    private fun buildMethod(
        signature: EtsMethodSignature,
        cfgBuilder: CfgBuilderContext.() -> Unit,
    ) = object : EtsMethod {
        override val signature = signature
        override val typeParameters: List<EtsType> = emptyList()
        override val modifiers: EtsModifiers = EtsModifiers.EMPTY
        override val decorators: List<EtsDecorator> = emptyList()
        override val cfg = CfgBuilderContext(this).apply(cfgBuilder).build()
        override val enclosingClass: EtsClass? = null
    }

    private fun parameters(n: Int) = List(n) {
        EtsMethodParameter(it, "a$it", EtsUnknownType)
    }
}
