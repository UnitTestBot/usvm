package org.usvm.fuzzer.generators

import io.leangen.geantyref.TypeFactory
import org.jacodb.api.JcClassType
import org.jacodb.api.ext.autoboxIfNeeded
import org.jacodb.api.ext.int
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.fuzzer.types.*
import org.usvm.fuzzer.util.createJcTypeWrapper
import org.usvm.instrumentation.util.toJavaClass
import java.net.URLClassLoader
import java.nio.file.Paths

class SimpleClassGenerationTest : GeneratorTest() {
    companion object {

        @BeforeAll
        @JvmStatic
        fun initClasspath() {
            testJarPath =
                listOf(
                    "/home/zver/IdeaProjects/usvm/usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation.jar",
                    "/home/zver/IdeaProjects/usvm/usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation-test.jar"
                )
            userClassLoader =
                URLClassLoader(testJarPath.map { Paths.get(it).toUri().toURL() }.toTypedArray())
            init()
            JcClassTable.initClasses(jcClasspath)
        }
    }

    @Test
    fun abstractClassGenerationTest() {
        val type = jcClasspath.findTypeOrNull("example.hierarchy.Computer") as JcClassType
        val genericReplacement = jcClasspath.int.autoboxIfNeeded().toJavaClass(userClassLoader)
        val jType = TypeFactory.parameterizedClass(type.toJavaClass(userClassLoader), genericReplacement)
        val tw = JcTypeWrapper(type, jType)
        val generator = generatorRepository.getGeneratorForType(tw)
        val generatedValue = generator.generate(0)
        println("GENERATED VALUE = $generatedValue")
    }

    @Test
    fun generateSimpleClass() {
        val type = jcClasspath.findTypeOrNull("example.GenericClass") as JcClassType
        val jType = type.toJavaClass(userClassLoader)
        val genericReplacement = jcClasspath.int.autoboxIfNeeded()
        val jRep = genericReplacement.toJavaClass(userClassLoader)
        val jcType = JcTypeWrapper(type, TypeFactory.parameterizedClass(jType, jRep))
        val generator = generatorRepository.getGeneratorForType(jcType)
        val generatedValue = generator.generate(0)
        println("GENERATED VALUE = $generatedValue")
    }

    @Test
    fun `class with complex generics`() {
        val type = jcClasspath.findTypeOrNull("example.fuzz.ComplexGenerics") as JcClassType
        val genericGenerator = JcGenericGeneratorImpl(jcClasspath, userClassLoader)
        val classWithReplacedGenerics = genericGenerator.resolveGenericParametersForType(
            JcTypeWrapper(type, type.toJavaClass(userClassLoader))
        )
        val generator = generatorRepository.getGeneratorForType(classWithReplacedGenerics)
        val generatedValue = generator.generate(0)
        println("GENERATED VALUE = $generatedValue")
    }

    @Test
    fun `class with static fields`() {
        val type = jcClasspath.findTypeOrNull("example.ClassWithStaticFields") as JcClassType
        val tw = type.createJcTypeWrapper(userClassLoader)
        val generator = generatorRepository.getGeneratorForType(tw)
        val generatedValue = generator.generate(0)
        println("GENERATED VALUE = $generatedValue")
    }

    @Test
    fun `complex generics`() {
        val type = jcClasspath.findTypeOrNull("example.GenericClassMap") as JcClassType
        val genericGenerator = JcGenericGeneratorImpl(jcClasspath, userClassLoader)
        val classWithReplacedGenerics = genericGenerator.resolveGenericParametersForType(
            JcTypeWrapper(type, type.toJavaClass(userClassLoader))
        )
        val generator = generatorRepository.getGeneratorForType(classWithReplacedGenerics)
        val generatedValue = generator.generate(0)
        println("GENERATED VALUE = $generatedValue")
    }

}