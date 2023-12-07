package org.usvm.fuzzer.generator

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.toType
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.types.JcGenericGeneratorImpl
import org.usvm.instrumentation.util.toJcType
import kotlin.system.exitProcess

class SeedGenerator(
    private val jcClasspath: JcClasspath,
    private val generatorRepository: GeneratorRepository
) {

    private val dataGenerator = DataGenerator(jcClasspath)
    private val genericGenerator = JcGenericGeneratorImpl(jcClasspath)

    fun generateForMethod(jcMethod: JcMethod): Seed {
        val jcClass = jcMethod.enclosingClass.toType()
        val resolvedClassType = genericGenerator.replaceGenericParametersForType(jcClass)
        val typedTargetMethod = resolvedClassType.declaredMethods.find { it.method == jcMethod } ?: error("Cant find method")
        val (resolvedMethod, methodSubstitutions) = genericGenerator.replaceGenericParametersForMethod(resolvedClassType, jcMethod)
        val classInstance =
            if (!jcMethod.isStatic) {
                with(generatorRepository.getGeneratorForType(resolvedClassType).generate()) {
                    Seed.Descriptor(instance, resolvedClassType, initStmts)
                }
            } else {
                null
            }
        val args =
            resolvedClassType.getMethodParametersTypes(typedTargetMethod, methodSubstitutions).map {
                with(generatorRepository.getGeneratorForType(it).generate()) {
                    Seed.Descriptor(instance, it, initStmts)
                }
            }
        return if (jcMethod.isStatic) {
            Seed(jcMethod, args, null)
        } else {
            Seed(jcMethod, listOf(classInstance!!) + args, null)
        }
    }
}