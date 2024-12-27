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

import org.jacodb.ets.base.CONSTRUCTOR_NAME
import org.jacodb.ets.base.EtsAddExpr
import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsInstLocation
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsParameterRef
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.graph.EtsCfg
import org.jacodb.ets.model.EtsClassImpl
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsDecorator
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodParameter
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsModifiers
import org.jacodb.ets.model.EtsScene
import org.usvm.dataflow.ts.infer.AccessPathBase
import org.usvm.dataflow.ts.infer.EtsTypeFact
import org.usvm.dataflow.ts.infer.TypeInferenceResult
import org.usvm.dataflow.ts.infer.annotation.EtsTypeAnnotator
import org.usvm.dataflow.ts.infer.verify.LocalId
import org.usvm.dataflow.ts.infer.verify.MethodId
import org.usvm.dataflow.ts.infer.verify.ParameterId
import org.usvm.dataflow.ts.infer.verify.VerificationResult
import org.usvm.dataflow.ts.infer.verify.verify
import kotlin.test.Test

internal class EtsTypeAnnotationTest {
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
                mainMethod to EtsTypeFact.StringEtsTypeFact
            ),
            inferredCombinedThisType = mapOf(
                mainClassSignature to EtsTypeFact.ObjectEtsTypeFact(
                    cls = EtsClassType(mainClassSignature),
                    properties = mapOf(),
                )
            )
        )

        val annotatedScene = EtsTypeAnnotator(sampleScene, typeInferenceResult)
            .annotateWithTypes(sampleScene)

        val verificationResult = verify(annotatedScene)

        assert(verificationResult is VerificationResult.Success)

        with(verificationResult as VerificationResult.Success) {
            val main = MethodId(mainMethodSignature)

            assert(mapping[ParameterId(1, main)] == EtsStringType)
            assert(mapping[ParameterId(2, main)] == EtsNumberType)

            assert(mapping[LocalId("v1", main)] == EtsStringType)
            assert(mapping[LocalId("v2", main)] == EtsNumberType)
            assert(mapping[LocalId("v3", main)] == EtsStringType)
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
        val arg1 = EtsParameterRef(1, EtsUnknownType)
        val arg2 = EtsParameterRef(2, EtsUnknownType)
        val v1 = EtsLocal("v1", EtsUnknownType)
        val v2 = EtsLocal("v2", EtsUnknownType)
        val v3 = EtsLocal("v3", EtsUnknownType)

        push(EtsAssignStmt(location, v1, arg1))
        push(EtsAssignStmt(location, v2, arg2))
        push(EtsAssignStmt(location, v3, EtsAddExpr(EtsUnknownType, v1, v2)))
        push(EtsReturnStmt(location, v3))
    }

    private val mainClassCtorSignature = EtsMethodSignature(
        enclosingClass = mainClassSignature,
        name = CONSTRUCTOR_NAME,
        parameters = parameters(1),
        returnType = EtsUnknownType,
    )

    private val mainClassCtor = buildMethod(mainClassCtorSignature) {
        val etsThis = EtsThis(EtsClassType(mainClassSignature))
        push(EtsReturnStmt(location, etsThis))
    }

    private val mainClass = EtsClassImpl(
        signature = mainClassSignature,
        fields = listOf(),
        methods = listOf(mainMethod),
        ctor = mainClassCtor,
        superClass = null,
    )

    private val mainFile = EtsFile(
        signature = mainTs,
        classes = listOf(mainClass),
        namespaces = listOf(),
    )

    private val sampleScene = EtsScene(listOf(mainFile))

    private class CfgBuilderContext(
        val method: EtsMethod,
    ) {
        private val stmts = mutableListOf<EtsStmt>()
        private val successorsMap = mutableMapOf<EtsStmt, MutableList<EtsStmt>>()

        fun build(): EtsCfg = EtsCfg(stmts, successorsMap)

        fun push(stmt: EtsStmt) {
            stmts.lastOrNull()?.let { link(it, stmt) }
            successorsMap[stmt] = mutableListOf()
            stmts.add(stmt)
        }

        fun link(from: EtsStmt, to: EtsStmt) {
            successorsMap.getOrPut(from, ::mutableListOf).add(to)
        }

        val location: EtsInstLocation
            get() = EtsInstLocation(method, stmts.size)
    }

    private fun buildMethod(
        signature: EtsMethodSignature,
        cfgBuilder: CfgBuilderContext.() -> Unit,
    ) = object : EtsMethod {
        override val signature = signature
        override val typeParameters: List<EtsType> = emptyList()
        override val modifiers: EtsModifiers = EtsModifiers.EMPTY
        override val decorators: List<EtsDecorator> = emptyList()
        override val locals: List<EtsLocal> = emptyList()
        override val cfg = CfgBuilderContext(this).apply(cfgBuilder).build()
    }

    private fun parameters(n: Int) =
        List(n) { EtsMethodParameter(it, "a$it", EtsUnknownType) }
}
