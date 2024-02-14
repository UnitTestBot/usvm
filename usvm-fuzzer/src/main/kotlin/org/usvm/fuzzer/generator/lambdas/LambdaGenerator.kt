package org.usvm.fuzzer.generator.lambdas

import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.void
import org.usvm.fuzzer.api.UTypedTestBooleanExpression
import org.usvm.fuzzer.api.UTypedTestInst
import org.usvm.fuzzer.api.UTypedTestLambdaMock
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.fuzzer.util.booleanTypeWrapper
import org.usvm.instrumentation.util.toJavaMethod
import org.usvm.instrumentation.util.toJcClass

class LambdaGenerator(private val lambdaType: JcTypeWrapper) : Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation? = gen@{ depth ->
        val initStmts = mutableListOf<UTypedTestInst>()
        val lambdaToGenerate =
            lambdaType.type.toJcClass()?.declaredMethods?.find { jcMethod ->
                val jMethod = jcMethod.toJavaMethod(userClassLoader)
                !jcMethod.isStatic && !jMethod.isDefault
            } ?: return@gen null
        val jLambda = lambdaToGenerate.toJavaMethod(userClassLoader)
        val resolvedReturnType = genericGenerator.resolveMethodReturnType(lambdaType, jLambda)
        val values = if (resolvedReturnType.type == jcClasspath.void) {
            listOf(UTypedTestBooleanExpression(false ,jcClasspath.booleanTypeWrapper()))
        }
        else {
            List(5) {
                val gen = repository.getGeneratorForType(resolvedReturnType)
                gen.generate(depth).let {
                    initStmts.addAll(it.initStmts)
                    it.instance
                }
            }
        }
        val lambda = UTypedTestLambdaMock(lambdaType, values)
        UTestValueRepresentation(lambda, initStmts)
    }
}