package org.usvm.fuzzer.generators

import org.jacodb.impl.types.JcClassTypeImpl
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.fuzzer.util.findResolvedTypeOrNull
import java.net.URLClassLoader
import java.nio.file.Paths
import kotlin.test.assertNotNull

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
        val type = jcClasspath.findResolvedTypeOrNull("java.util.ArrayList<java.lang.Integer>")
        val generator = generatorRepository.getGeneratorForType(type) ?: error("Cant find ArrayList generator")
        val generatedValue = generator.generate()
        assertNotNull(generatedValue)
    }
}
