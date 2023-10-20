package org.usvm.fuzzer.generator.arrays

import org.jacodb.api.JcType
import org.jacodb.api.ext.int
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestArraySetStatement
import org.usvm.instrumentation.testcase.api.UTestCreateArrayExpression
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestIntExpression

class ArrayGenerator(private val component: JcType) : Generator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val length = random.nextInt(GeneratorSettings.minCollectionSize, GeneratorSettings.maxCollectionSize)
        val arrayInstance = UTestCreateArrayExpression(component, UTestIntExpression(length, jcClasspath.int))
        val componentGenerator = repository.getGeneratorForType(component) ?: error("Can't find generator for type $component")
        val initStatements = mutableListOf<UTestInst>()
        val valuesSetters = (0..length).map { index ->
            componentGenerator.generationFun.invoke(ctx).let {
                initStatements.addAll(it.initStmts)
                UTestArraySetStatement(arrayInstance, UTestIntExpression(index, jcClasspath.int), it.instance)
            }
        }
        UTestValueRepresentation(arrayInstance, initStatements + valuesSetters)
    }
}