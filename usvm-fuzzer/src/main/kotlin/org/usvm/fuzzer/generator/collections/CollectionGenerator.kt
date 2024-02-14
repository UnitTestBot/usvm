package org.usvm.fuzzer.generator.collections

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.methods
import org.usvm.fuzzer.api.UTypedTestConstructorCall
import org.usvm.fuzzer.api.UTypedTestInst
import org.usvm.fuzzer.api.UTypedTestMethodCall
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.nextInt
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestConstructorCall
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestMethodCall

open class CollectionGenerator(
    val collectionClass: JcClassOrInterface,
    val realType: JcTypeWrapper,
    val functionNameForAdd: String
) : Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = { depth ->
        val length = random.nextInt(GeneratorSettings.minCollectionSize, GeneratorSettings.maxCollectionSize)
        println("GENERATING COLLECTIONS OF TYPE ${collectionClass.name} with length $length")
        val constructor = collectionClass.constructors.first { it.parameters.isEmpty() }
        val constructorCall = UTypedTestConstructorCall(constructor, listOf(), realType)
        val componentGenerators = realType.typeArguments.map { repository.getGeneratorForType(it) }
//        val componentGenerators = realType.substitutions.map { repository.getGeneratorForType(it.substitution) }
        val addFun =
            (collectionClass.declaredMethods + collectionClass.methods).find { it.name == functionNameForAdd && it.parameters.size == componentGenerators.size }
                ?: error("add fun does not exist for collection ${collectionClass.name} and args ${componentGenerators.size}")
        val initStatements = mutableListOf<UTypedTestInst>()
        val addInvocations = (0 until length).map { index ->
            val argsForAddInvocation = componentGenerators
                .map {
                    it.generate(depth).let {
                        initStatements.addAll(it.initStmts)
                        it.instance
                    }
                }
            UTypedTestMethodCall(constructorCall, addFun, argsForAddInvocation)
        }
        UTestValueRepresentation(constructorCall, initStatements + addInvocations)
    }

}