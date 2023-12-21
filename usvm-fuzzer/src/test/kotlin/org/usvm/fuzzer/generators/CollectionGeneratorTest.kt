package org.usvm.fuzzer.generators

import io.leangen.geantyref.GenericTypeReflector
import io.leangen.geantyref.TypeFactory
import org.jacodb.api.JcClassType
import org.jacodb.api.ext.autoboxIfNeeded
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.int
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.types.Substitution
import org.usvm.instrumentation.util.toJavaClass
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
        val type = jcClasspath.findTypeOrNull("java.util.ArrayList") as JcClassType
        val genericReplacement = jcClasspath.int.autoboxIfNeeded()
        val jType = type.toJavaClass(userClassLoader)
        val jReplacement = genericReplacement.toJavaClass(userClassLoader)
        val jResolvedType = TypeFactory.parameterizedClass(jType, jReplacement)
        val tw = JcTypeWrapper(type, jResolvedType)
        val generator = generatorRepository.getGeneratorForType(tw)
        val generatedValue = generator.generate(0)
        assertNotNull(generatedValue)
    }
}
