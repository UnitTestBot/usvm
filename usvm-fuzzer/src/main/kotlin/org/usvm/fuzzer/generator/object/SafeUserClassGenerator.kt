package org.usvm.fuzzer.generator.`object`

import org.jacodb.api.JcClassType
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.ext.constructors
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestConstructorCall
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestNullExpression
import kotlin.random.Random

open class SafeUserClassGenerator(private val jcTypeWrapper: JcTypeWrapper) : UserClassGenerator() {

    override val generationFun: GeneratorContext.() -> UTestValueRepresentation? = {
        val randomInt = Random.nextInt(0, 10)
        when {
            randomInt in 0..5 && jcTypeWrapper.constructors.isNotEmpty() -> callRandomConstructor(this)
            randomInt in 6..7 && isRandomStaticMethodAvailable() -> callRandomStaticMethod(this)
            randomInt in 8..9 && isRandomStaticFieldAvailable() -> getRandomStaticFieldValue(this)
            else -> when {
                jcTypeWrapper.constructors.isNotEmpty() -> callRandomConstructor(this)
                isRandomStaticMethodAvailable() -> callRandomStaticMethod(this)
                isRandomStaticFieldAvailable() -> getRandomStaticFieldValue(this)
                else -> null
            }
        }
    }

    private fun isRandomStaticMethodAvailable() = false
    private fun isRandomStaticFieldAvailable() = false


    protected fun callRandomStaticMethod(ctx: GeneratorContext): UTestValueRepresentation {
        return UTestValueRepresentation(UTestNullExpression(jcTypeWrapper.type))
    }

    protected fun getRandomStaticFieldValue(ctx: GeneratorContext): UTestValueRepresentation {
        return UTestValueRepresentation(UTestNullExpression(jcTypeWrapper.type))
    }

    protected fun callRandomConstructor(ctx: GeneratorContext): UTestValueRepresentation? =
        getRandomWeighedConstructor(jcTypeWrapper, ctx.random)?.let { randomConstructor ->
            val initStmts = mutableListOf<UTestInst>()
            val args =
                jcTypeWrapper.getMethodParametersTypes(randomConstructor).map { paramType ->
                    val gen = ctx.repository.getGeneratorForType(paramType)
                    gen.generate().let {
                        initStmts.addAll(it.initStmts)
                        it.instance
                    }
                }
            val instance = UTestConstructorCall(randomConstructor.method, args)
            UTestValueRepresentation(instance, initStmts)
        }


    protected fun getRandomWeighedConstructor(type: JcTypeWrapper, random: Random): JcTypedMethod? {
        if (type.constructors.isEmpty()) return null
        if (type.constructors.size == 1) return type.constructors.first()
        val (maxParams, minParams) = with(type.constructors) {
            maxOf { it.parameters.size } to minOf { it.parameters.size }
        }
        val diffOfParams = maxParams - minParams + 1
        var sumOfWeights = 0
        val constructorToWeight =
            type.constructors.map { it to ((diffOfParams - it.parameters.size) * (diffOfParams - it.parameters.size)).also { sumOfWeights += it } }
        var randomWeight = random.nextInt(0, sumOfWeights)
        constructorToWeight.forEach {
            randomWeight -= it.second
            if (randomWeight <= 0) return it.first
        }
        return null
    }

}