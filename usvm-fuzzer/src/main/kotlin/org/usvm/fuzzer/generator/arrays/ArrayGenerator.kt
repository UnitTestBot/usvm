package org.usvm.fuzzer.generator.arrays

import org.jacodb.api.JcArrayType
import org.jacodb.api.ext.int
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.generator.random.nextInt
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestArraySetStatement
import org.usvm.instrumentation.testcase.api.UTestCreateArrayExpression
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestIntExpression

class ArrayGenerator(
    private val arrayType: JcTypeWrapper,
    // variable for optionally generate arrays of consistent length
    private val arraySize: Int = -1
) : Generator() {


    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = { depth ->
        val length =
            if (arraySize != -1) {
                arraySize
            } else {
                random.nextInt(GeneratorSettings.minCollectionSize, GeneratorSettings.maxCollectionSize)
            }
        val jcArrayType = (arrayType.type as JcArrayType)
        val elementType = jcArrayType.elementType
        val resolvedElementType = arrayType.getArrayElementType()!!
        val arrayInstance = UTestCreateArrayExpression(elementType, UTestIntExpression(length, jcClasspath.int))
        val componentGenerator =
            if (resolvedElementType.type is JcArrayType && random.getTrueWithProb(30)) {
                ArrayGenerator(resolvedElementType, length).also { it.ctx = this }
            } else {
                repository.getGeneratorForType(resolvedElementType)
            }
        val initStatements = mutableListOf<UTestInst>()
        val valuesSetters = (0 until length).map { index ->
            componentGenerator.generate(depth).let {
                initStatements.addAll(it.initStmts)
                UTestArraySetStatement(arrayInstance, UTestIntExpression(index, jcClasspath.int), it.instance)
            }
        }
        UTestValueRepresentation(arrayInstance, initStatements + valuesSetters)
    }
}