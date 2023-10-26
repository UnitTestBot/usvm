package org.usvm.fuzzer.generators

import org.jacodb.impl.types.JcClassTypeImpl
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.usvm.fuzzer.types.*
import org.usvm.fuzzer.util.findResolvedTypeOrNull
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
        val type = jcClasspath.findResolvedTypeOrNull("example.hierarchy.Computer")
        val generator = generatorRepository.getGeneratorForType(type) ?: error("Cant find ArrayList generator")
        val generatedValue = generator.generate()
        println("GENERATED VALUE = $generatedValue")
    }

    @Test
    fun generateSimpleClass() {
        val jcType = jcClasspath.findResolvedTypeOrNull("example.GenericClass<java.lang.Integer>")
        val generator = generatorRepository.getGeneratorForType(jcType) ?: error("Cant find ArrayList generator")
        val generatedValue = generator.generate()
        println("GENERATED VALUE = $generatedValue")
    }

    @Test
    fun `class with static fields`() {
        val jcType = jcClasspath.findResolvedTypeOrNull("example.ClassWithStaticFields")
        val generator = generatorRepository.getGeneratorForType(jcType) ?: error("Cant find ArrayList generator")
        val generatedValue = generator.generate()
        println("GENERATED VALUE = $generatedValue")
    }

    @Test
    fun `complex generics`() {

        val t = "example.GenericClassMap<java.util.Map<java.lang.Integer, java.lang.String>>"
        val jcType = JcType2JvmTypeConverter.convertToJcTypeWrapper(t, jcClasspath)
        val generator = generatorRepository.getGeneratorForType(jcType)
        val generatedValue = generator.generate()
        println(generatedValue)



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