package org.usvm.fuzzer.generator.arrays

import org.jacodb.api.JcArrayType
import org.jacodb.api.ext.int
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestArraySetStatement
import org.usvm.instrumentation.testcase.api.UTestCreateArrayExpression
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestIntExpression

class ArrayGenerator(private val arrayType: JcTypeWrapper) : Generator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val length = random.nextInt(GeneratorSettings.minCollectionSize, GeneratorSettings.maxCollectionSize)
        val jcArrayType = (arrayType.type as JcArrayType)
        val elementType = jcArrayType.elementType
        val resolvedElementType = arrayType.resolveJcType(elementType)
        val arrayInstance = UTestCreateArrayExpression(elementType, UTestIntExpression(length, jcClasspath.int))
        val componentGenerator = repository.getGeneratorForType(resolvedElementType) ?: error("Can't find generator for type $jcArrayType")
        val initStatements = mutableListOf<UTestInst>()
        val valuesSetters = (0 until length).map { index ->
            componentGenerator.generate().let {
                initStatements.addAll(it.initStmts)
                UTestArraySetStatement(arrayInstance, UTestIntExpression(index, jcClasspath.int), it.instance)
            }
        }
        UTestValueRepresentation(arrayInstance, initStatements + valuesSetters)
    }
}