package org.usvm.fuzzer.generator.`object`

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestMockObject

class MockClassGenerator(private val jcType: JcTypeWrapper): UserClassGenerator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val initStmts = mutableListOf<UTestInst>()
        val fields = jcType.declaredFields.associate { jcField ->
            val fieldType = jcType.getFieldType(jcField)
            val generatorForField = repository.getGeneratorForType(fieldType)
            val fieldValue = generatorForField.generate()
            initStmts.addAll(fieldValue.initStmts)
            jcField.field to fieldValue.instance
        }
        val methods = jcType.declaredMethods.associate { jcMethod ->
            val methodReturnType = jcType.getMethodReturnType(jcMethod)
            val generatorForMethodRetType = repository.getGeneratorForType(methodReturnType)
            val methodValue = generatorForMethodRetType.generate()
            initStmts.addAll(methodValue.initStmts)
            jcMethod.method to listOf(methodValue.instance)
        }
        val mock = UTestMockObject(jcType.type, fields, methods)
        UTestValueRepresentation(mock, initStmts)
    }
}