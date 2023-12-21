package org.usvm.fuzzer.generator

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.toType
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.types.JcGenericGeneratorImpl
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.instrumentation.util.toJavaClass
import org.usvm.instrumentation.util.toJavaMethod

class SeedGenerator(
    private val jcClasspath: JcClasspath,
    private val generatorRepository: GeneratorRepository,
    private val userClassLoader: ClassLoader
) {

    private val dataGenerator = DataGenerator(jcClasspath)
    private val genericGenerator = JcGenericGeneratorImpl(jcClasspath, userClassLoader)

    fun init(jcMethod: JcMethod) {
        val numberOfArgs =
            if (jcMethod.isStatic) {
                jcMethod.parameters.size
            } else {
                jcMethod.parameters.size + 1
            }
        Seed.treesForArgs = List(numberOfArgs) { org.usvm.fuzzer.position.PositionTrie() }
    }

    fun generateForMethod(jcMethod: JcMethod): Seed {
        init(jcMethod)
        val jcClass = jcMethod.enclosingClass.toType()
        val resolvedClassType = genericGenerator.resolveGenericParametersForType(
            JcTypeWrapper(
                jcClass,
                jcClass.toJavaClass(userClassLoader)
            )
        )
        val typedTargetMethod =
            resolvedClassType.declaredMethods.find { it.method == jcMethod } ?: error("Cant find method")
        val jTargetMethod = typedTargetMethod.method.toJavaMethod(userClassLoader)
//        val (resolvedMethod, methodSubstitutions) = genericGenerator.replaceGenericParametersForMethod(resolvedClassType, jcMethod)
        val classInstance =
            if (!jcMethod.isStatic) {
                with(generatorRepository.getGeneratorForType(resolvedClassType).generate()) {
                    Seed.Descriptor(instance, resolvedClassType, initStmts)
                }
            } else {
                null
            }
        val args =
            genericGenerator
                .resolveGenericParametersForMethod(resolvedClassType, jTargetMethod)
                .second
                .map {
                    with(generatorRepository.getGeneratorForType(it).generate()) {
                        Seed.Descriptor(instance, it, initStmts)
                    }
                }
        println("BUILDING SEED FROM GENERATED ARGS!!")
        return if (jcMethod.isStatic) {
            Seed(jcMethod, args, null)
        } else {
            Seed(jcMethod, listOf(classInstance!!) + args, null)
        }.also { println("SEED IS BUILT") }
    }
}