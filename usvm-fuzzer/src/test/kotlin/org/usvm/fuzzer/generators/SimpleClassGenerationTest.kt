package org.usvm.fuzzer.generators

import kotlinx.collections.immutable.toPersistentMap
import org.jacodb.api.ext.autoboxIfNeeded
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.int
import org.jacodb.api.ext.unboxIfNeeded
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.fuzzer.types.*
import org.usvm.instrumentation.util.stringType
import org.usvm.instrumentation.util.zipToMap
import java.net.URLClassLoader
import java.nio.file.Paths

class SimpleClassGenerationTest: GeneratorTest() {
    companion object {

        @BeforeAll
        @JvmStatic
        fun initClasspath() {
            testJarPath =
                listOf("/home/zver/IdeaProjects/usvm/usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation.jar",
                    "/home/zver/IdeaProjects/usvm/usvm-jvm-instrumentation/build/libs/usvm-jvm-instrumentation-test.jar")
            userClassLoader = URLClassLoader(arrayOf(Paths.get(testJarPath.first()).toUri().toURL()), this::class.java.classLoader)
            init()
        }
    }

    @Test
    fun abstractClassGenerationTest() {
        val type = jcClasspath.findTypeOrNull("example.hierarchy.Computer") as JcClassTypeImpl
        val generator = generatorRepository.getGeneratorForType(type) ?: error("Cant find ArrayList generator")
        val generatedValue = generator.generate()
        println("GENERATED VALUE = $generatedValue")
    }

    @Test
    fun generateSimpleClass() {
        val type = jcClasspath.findTypeOrNull("example.GenericClass") as JcClassTypeImpl
        val genericReplacement = jcClasspath.findTypeOrNull("java.lang.Integer")!!
        val newType = type.getResolvedType(listOf(genericReplacement))
        val generator = generatorRepository.getGeneratorForType(newType) ?: error("Cant find ArrayList generator")
        val generatedValue = generator.generate()
        println("GENERATED VALUE = $generatedValue")
    }

    @Test
    fun `class with static fields`() {
        val type = jcClasspath.findTypeOrNull("example.ClassWithStaticFields") as JcClassTypeImpl
        val generator = generatorRepository.getGeneratorForType(type) ?: error("Cant find ArrayList generator")
        val generatedValue = generator.generate()
        println("GENERATED VALUE = $generatedValue")
    }

    @Test
    fun `complex generics`() {

        val t = "example.GenericClassMap<java.util.Map<java.lang.Integer, java.lang.String>>"
        val jcType = JcType2JvmTypeConverter.convertToJcType(t, jcClasspath)
        println("TYPPE = $jcType")
        val t1 = jcType
        val c = t1.getConstructors().first()
//        val generator = generatorRepository.getGeneratorForType()



//
//        val type = jcClasspath.findTypeOrNull("example.GenericClassMap") as JcClassTypeImpl
//        val genericReplacement = jcClasspath.findTypeOrNull("java.util.Map") as JcClassTypeImpl
//        val mapType = genericReplacement.getResolvedType(listOf(jcClasspath.int.autoboxIfNeeded(), jcClasspath.stringType()))
//        val newType = type.getResolvedType(listOf(mapType))
//        val a = newType.constructors.first().parameters.first().type as JcClassTypeImpl
//        mapType.declaredMethods.map { it.name + " " + it.returnType.typeName }
//        val r = listOf(jcClasspath.int.autoboxIfNeeded(), jcClasspath.stringType())
//        val m1 = mapType.typeParameters
//            .map { it.convertToJvmTypeParameterDeclarationImpl() }
//            .zipToMap(r.map { it.convertToJvmType() }).toPersistentMap()
//        val m2 = type.typeParameters
//            .map { it.convertToJvmTypeParameterDeclarationImpl() }
//            .zipToMap(listOf(mapType).map { it.convertToJvmType() }).toPersistentMap()
//        val t = JcClassTypeImpl(
//            newType.classpath,
//            newType.name,
//            newType.outerType,
//            JcSubstitutorImpl(
//                (m1 + m2).toPersistentMap()
//            ),
//            newType.nullable,
//            newType.annotations
//        )
//        val ttt = t.declaredMethods.first().parameters.first().type
//        println(ttt)
//
//        println("NEW TYPE = $newType")
    }

}