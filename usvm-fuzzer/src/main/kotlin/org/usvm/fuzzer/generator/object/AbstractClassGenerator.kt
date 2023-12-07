package org.usvm.fuzzer.generator.`object`

import org.jacodb.api.JcClassType
import org.jacodb.api.NoClassInClasspathException
import org.jacodb.api.ext.allSuperHierarchy
import org.jacodb.api.ext.toType
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.types.JcClassTable
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.fuzzer.util.getTrueWithProb

class SafeAbstractClassGenerator(private val jcType: JcTypeWrapper) : SafeUserClassGenerator(jcType) {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation? = {
        val jcClass = (jcType.type as JcClassType).jcClass
        val randomImplementer =
            JcClassTable.getRandomSubclassOf(listOf(jcClass))
        if (randomImplementer != null && random.getTrueWithProb(100 - GeneratorSettings.mockGenerationProbability)) {
            val randomImplementerType = genericGenerator.replaceGenericsForSubtypeOf(randomImplementer, jcType, userClassLoader)
            repository.getGeneratorForType(randomImplementerType).generate()
        } else {
            repository.getMockGeneratorForType(jcType).generate()
        }
    }
}