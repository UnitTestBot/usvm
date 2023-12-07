package org.usvm.fuzzer.generator.collections

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.methods
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
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
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val length = random.nextInt(GeneratorSettings.minCollectionSize, GeneratorSettings.maxCollectionSize)
        val constructor = collectionClass.constructors.first { it.parameters.isEmpty() }
        val constructorCall = UTestConstructorCall(constructor, listOf())
        val componentGenerators = realType.substitutions.map { repository.getGeneratorForType(it.substitution) }
        val addFun =
            collectionClass.methods.find { it.name == functionNameForAdd && it.parameters.size == componentGenerators.size }
                ?: error("add fun does not exist")
        val initStatements = mutableListOf<UTestInst>()
        val addInvocations = (0..length).map { index ->
            val argsForAddInvocation = componentGenerators
                .map {
                    it.generate().let {
                        initStatements.addAll(it.initStmts)
                        it.instance
                    }
                }
            UTestMethodCall(constructorCall, addFun, argsForAddInvocation)
        }
        UTestValueRepresentation(constructorCall, initStatements + addInvocations)
    }

}