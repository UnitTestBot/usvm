package org.usvm.fuzzer.generator.collections

import org.jacodb.api.JcClassType
import org.jacodb.api.ext.constructors
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestConstructorCall
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestMethodCall

open class CollectionGenerator(val collectionType: JcClassType): Generator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val length = random.nextInt(GeneratorSettings.minCollectionSize, GeneratorSettings.maxCollectionSize)
        val constructor = collectionType.constructors.first { it.parameters.isEmpty() }
        val constructorCall = UTestConstructorCall(constructor.method, listOf())
        val componentType = collectionType.typeArguments.first()
        val componentGenerator = repository.getGeneratorForType(componentType) ?: error("cant find generator for type ${componentType.typeName}")
        val addFun = collectionType.declaredMethods.find { it.name == "add" && it.parameters.size == 1 }?.method ?: error("add fun does not exist")
        val initStatements = mutableListOf<UTestInst>()
        val addInvocations = (0..length).map { index ->
            componentGenerator.generationFun.invoke(ctx).let {
                initStatements.addAll(it.initStmts)
                UTestMethodCall(constructorCall, addFun, listOf(it.instance))
            }
        }
        UTestValueRepresentation(constructorCall, initStatements + addInvocations)
    }

}