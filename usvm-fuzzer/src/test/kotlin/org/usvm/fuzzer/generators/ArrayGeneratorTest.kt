package org.usvm.fuzzer.generators

import org.jacodb.api.ext.int
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class ArrayGeneratorTest: GeneratorTest() {
    companion object {

        @BeforeAll
        @JvmStatic
        fun initClasspath() {
            testJarPath = listOf("/home/zver/IdeaProjects/usvm/usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation.jar")
            init()
        }
    }

    @Test
    fun testSimpleArrayCreation() {
        val simpleArrayType = jcClasspath.arrayTypeOf(jcClasspath.int)
        val generator = generatorRepository.getGeneratorForUnresolvedType(simpleArrayType)
        assertNotNull(generator)
        assertNotNull(generator.generate())
    }

    @Test
    fun testMultiDimensionalArrayCreation() {
        val twoDimArrayType = jcClasspath.arrayTypeOf(jcClasspath.arrayTypeOf(jcClasspath.int))
        val generator = generatorRepository.getGeneratorForUnresolvedType(twoDimArrayType)
        assertNotNull(generator)
        assertNotNull(generator.generate())
    }
}