package org.usvm.fuzzer.generators

import io.leangen.geantyref.TypeFactory
import org.jacodb.api.ext.autoboxIfNeeded
import org.jacodb.api.ext.int
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.listType
import org.usvm.instrumentation.util.toJavaClass
import java.net.URLClassLoader
import java.nio.file.Paths
import kotlin.test.assertNotNull

class ArrayGeneratorTest : GeneratorTest() {
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
    fun testSimpleArrayCreation() {
        val simpleArrayType = jcClasspath.arrayTypeOf(jcClasspath.int)
        val generator = generatorRepository.getGeneratorForType(
            JcTypeWrapper(
                simpleArrayType, simpleArrayType.toJavaClass(
                    userClassLoader
                )
            )
        )
        assertNotNull(generator)
        assertNotNull(generator.generate(0))
    }

    @Test
    fun testArrayOfListsCreation() {
        val listType = jcClasspath.listType()
        val listGenericReplacement = jcClasspath.int.autoboxIfNeeded()
        val jListType = listType.toJavaClass(userClassLoader)
        val jRep = listGenericReplacement.toJavaClass(userClassLoader)
        val jArrayType = TypeFactory.arrayOf(TypeFactory.parameterizedClass(jListType, jRep))
        val arrayType = jcClasspath.arrayTypeOf(jcClasspath.listType())
        val jcType = JcTypeWrapper(arrayType, jArrayType)
        val generator = generatorRepository.getGeneratorForType(jcType)
        assertNotNull(generator)
        assertNotNull(generator.generate(0))
    }

    @Test
    fun testMultiDimensionalArrayCreation() {
        val twoDimArrayType = jcClasspath.arrayTypeOf(jcClasspath.arrayTypeOf(jcClasspath.int))
        val generator = generatorRepository.getGeneratorForType(
            JcTypeWrapper(
                twoDimArrayType, twoDimArrayType.toJavaClass(
                    userClassLoader
                )
            )
        )
        assertNotNull(generator)
        assertNotNull(generator.generate(0))
    }
}