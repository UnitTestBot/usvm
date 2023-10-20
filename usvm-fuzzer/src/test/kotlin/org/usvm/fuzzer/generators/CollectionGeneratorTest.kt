package org.usvm.fuzzer.generators

import org.jacodb.impl.types.JcClassTypeImpl
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.fuzzer.types.getResolvedType
import java.net.URLClassLoader
import java.nio.file.Paths

class CollectionGeneratorTest: GeneratorTest() {
    companion object {

        @BeforeAll
        @JvmStatic
        fun initClasspath() {
            testJarPath =
                listOf("/home/zver/IdeaProjects/usvm/usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation.jar")
            userClassLoader = URLClassLoader(arrayOf(Paths.get(testJarPath.first()).toUri().toURL()), this::class.java.classLoader)
            init()
        }
    }

    @Test
    fun generateSimpleArrayList() {
        val type = jcClasspath.findTypeOrNull("java.util.ArrayList") as JcClassTypeImpl
        val genericReplacement = jcClasspath.findTypeOrNull("java.lang.Integer")!!
        val newType = type.getResolvedType(listOf(genericReplacement))
        val generator = generatorRepository.getGeneratorForType(newType) ?: error("Cant find ArrayList generator")
        val generatedValue = generator.generate()
        println("GEN VALUE = $generatedValue")
    }
}
