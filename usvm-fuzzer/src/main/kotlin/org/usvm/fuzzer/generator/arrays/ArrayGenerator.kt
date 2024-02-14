package org.usvm.fuzzer.generator.arrays

import org.jacodb.api.JcArrayType
import org.jacodb.api.ext.int
import org.usvm.fuzzer.api.UTypedTestArraySetStatement
import org.usvm.fuzzer.api.UTypedTestCreateArrayExpression
import org.usvm.fuzzer.api.UTypedTestInst
import org.usvm.fuzzer.api.UTypedTestIntExpression
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.generator.random.nextInt
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.fuzzer.util.createJcTypeWrapper
import org.usvm.fuzzer.util.intTypeWrapper
import org.usvm.instrumentation.testcase.api.UTestIntExpression
import java.lang.reflect.GenericArrayType

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
        val resolvedElementType = arrayType.getArrayElementType()!!
        val arrayInstance = UTypedTestCreateArrayExpression(resolvedElementType, UTypedTestIntExpression(length, jcClasspath.intTypeWrapper()), arrayType)
        val componentGenerator =
            if (resolvedElementType.type is JcArrayType && random.getTrueWithProb(30)) {
                ArrayGenerator(resolvedElementType, length).also { it.ctx = this }
            } else {
                repository.getGeneratorForType(resolvedElementType)
            }
        val initStatements = mutableListOf<UTypedTestInst>()
        val valuesSetters = (0 until length).map { index ->
            componentGenerator.generate(depth).let {
                initStatements.addAll(it.initStmts)
                UTypedTestArraySetStatement(arrayInstance, UTypedTestIntExpression(index, jcClasspath.intTypeWrapper()), it.instance)
            }
        }
        UTestValueRepresentation(arrayInstance, initStatements + valuesSetters)
    }
}