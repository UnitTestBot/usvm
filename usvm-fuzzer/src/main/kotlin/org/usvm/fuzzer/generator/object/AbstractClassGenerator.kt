package org.usvm.fuzzer.generator.`object`

import org.jacodb.api.JcClassType
import org.jacodb.api.NoClassInClasspathException
import org.jacodb.api.ext.allSuperHierarchy
import org.jacodb.api.ext.toType
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.fuzzer.util.getTrueWithProb

class SafeAbstractClassGenerator(private val jcType: JcClassType) : SafeUserClassGenerator(jcType) {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation? = {
        val randomImplementer =
            classes
            .filterNot { it.isAbstract || it.isInterface }
            .filter {
                try {
                    it.allSuperHierarchy.contains(jcType.jcClass)
                } catch (e: NoClassInClasspathException) {
                    false
                }
            }
            .randomOrNull()
        if (randomImplementer != null && random.getTrueWithProb(100 - GeneratorSettings.mockGenerationProbability)) {
            repository.getGeneratorForType(randomImplementer.toType()).generate()
        } else {
            repository.getMockGeneratorForType(jcType).generate()
        }
    }
}