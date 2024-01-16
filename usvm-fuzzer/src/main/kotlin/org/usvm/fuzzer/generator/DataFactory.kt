package org.usvm.fuzzer.generator

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.toType
import org.usvm.fuzzer.mutation.MutationRepository
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.strategy.ChoosingStrategy
import org.usvm.fuzzer.strategy.RandomStrategy
import org.usvm.fuzzer.types.JcGenericGeneratorImpl
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.util.toJavaClass
import org.usvm.instrumentation.util.toJavaMethod
import java.util.Random

class DataFactory(
    val jcClasspath: JcClasspath,
    val generatorRepository: GeneratorRepository,
    val userClassLoader: ClassLoader,
    val random: Random,
    val seedArgsChoosingStrategy: ChoosingStrategy<Seed.ArgumentDescriptor>
) {
    private val genericGenerator = JcGenericGeneratorImpl(jcClasspath, userClassLoader)
    fun generateValuesForMethodInvocation(
        resolvedClassType: JcTypeWrapper,
        jcMethod: JcMethod
    ): List<UTestValueRepresentation> {
        val jTargetMethod = jcMethod.toJavaMethod(userClassLoader)
        return genericGenerator
            .resolveGenericParametersForMethod(resolvedClassType, jTargetMethod)
            .second
            .map { generateValueOfType(it) }
    }

    fun generateValueOfType(jcTypeWrapper: JcTypeWrapper): UTestValueRepresentation =
        generatorRepository.getGeneratorForType(jcTypeWrapper).generate()

    fun generateSeedsForMethod(jcMethod: JcMethod): Seed {
        val jcClass = jcMethod.enclosingClass.toType()
        val resolvedClassType = genericGenerator.resolveGenericParametersForType(
            JcTypeWrapper(
                jcClass,
                jcClass.toJavaClass(userClassLoader)
            )
        )
//        val (resolvedMethod, methodSubstitutions) = genericGenerator.replaceGenericParametersForMethod(resolvedClassType, jcMethod)
        val classInstance =
            if (!jcMethod.isStatic) {
                with(generateValueOfType(resolvedClassType)) {
                    Seed.ArgumentDescriptor(instance, resolvedClassType, initStmts)
                }
            } else {
                null
            }
        val args =
            generateValuesForMethodInvocation(resolvedClassType, jcMethod)
                .map { Seed.ArgumentDescriptor(it.instance, resolvedClassType, it.initStmts) }
        println("BUILDING SEED FROM GENERATED ARGS!!")
        return if (jcMethod.isStatic) {
            Seed(jcMethod, args, seedArgsChoosingStrategy)
        } else {
            Seed(jcMethod, listOf(classInstance!!) + args, seedArgsChoosingStrategy)
        }.also { println("SEED IS BUILT") }
    }


//    private fun mutate(seed: Seed, iteration: Int): Seed? {
//        val bestPositionToMutate = seed.getPositionToMutate(iteration)
//        return mutationRepository.getMutation(iteration).mutate(seed, bestPositionToMutate.index)
//    }
}