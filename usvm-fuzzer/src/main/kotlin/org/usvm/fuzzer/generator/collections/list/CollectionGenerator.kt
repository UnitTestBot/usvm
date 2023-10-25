package org.usvm.fuzzer.generator.collections.list

import org.jacodb.api.JcClassType
import org.jacodb.api.ext.constructors
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestConstructorCall
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestMethodCall

open class CollectionGenerator(val collectionType: JcClassType, val functionNameForAdd: String) : Generator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val length = random.nextInt(GeneratorSettings.minCollectionSize, GeneratorSettings.maxCollectionSize)
        val constructor = collectionType.constructors.first { it.parameters.isEmpty() }
        val constructorCall = UTestConstructorCall(constructor.method, listOf())
        val componentTypes = collectionType.typeArguments
        val componentGenerators = componentTypes.map { repository.getGeneratorForType(it) }
        val addFun =
            collectionType.declaredMethods.find { it.name == functionNameForAdd && it.parameters.size == componentTypes.size }?.method
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