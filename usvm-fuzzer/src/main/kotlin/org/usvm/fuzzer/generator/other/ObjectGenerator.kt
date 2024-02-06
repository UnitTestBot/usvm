package org.usvm.fuzzer.generator.other

import org.jacodb.api.ext.objectClass
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.types.JcClassTable
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.fuzzer.util.createJcTypeWrapper

class ObjectGenerator: Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation? = { depth ->
        val randomClass = JcClassTable.getRandomSubclassOf(listOf(jcClasspath.objectClass))!!.createJcTypeWrapper(userClassLoader)
        val resolvedRandomClass = genericGenerator.resolveGenericParametersForType(randomClass)
        val generator = repository.getGeneratorForType(resolvedRandomClass)
        generator.generate(depth)
    }
}