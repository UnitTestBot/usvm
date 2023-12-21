package org.usvm.fuzzer.generator.`object`

import org.jacodb.api.JcClassType
import org.jacodb.api.ext.toType
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.types.JcClassTable
import org.usvm.fuzzer.types.JcGenericGeneratorImpl
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.util.toJavaClass

class SafeAbstractClassGenerator(private val jcType: JcTypeWrapper) : SafeUserClassGenerator(jcType) {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation? = { depth ->
        val jcClass = (jcType.type as JcClassType).jcClass
        val randomImplementer = JcClassTable.getRandomSubclassOf(listOf(jcClass))?.toType()
        if (randomImplementer != null && random.getTrueWithProb(100 - GeneratorSettings.mockGenerationProbability)) {
            val jImplementer = randomImplementer.toJavaClass(userClassLoader)
            val randomImplementerType = genericGenerator.replaceGenericsForSubtypeOf(
                JcTypeWrapper(randomImplementer, jImplementer),
                jcType
            )
            repository.getGeneratorForType(randomImplementerType).generate(depth)
        } else {
            repository.getMockGeneratorForType(jcType).generate(depth)
        }
    }
}