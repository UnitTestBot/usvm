package org.usvm.fuzzer.generator.`object`

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestMockObject

class MockClassGenerator(private val jcType: JcClassType): UserClassGenerator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val initStmts = mutableListOf<UTestInst>()
        val fields = jcType.declaredFields.associate { jcField ->
            val generatorForField = repository.getGeneratorForType(jcField.fieldType)
            val fieldValue = generatorForField.generate()
            initStmts.addAll(fieldValue.initStmts)
            jcField.field to fieldValue.instance
        }
        val methods = jcType.declaredMethods.associate { jcMethod ->
            val generatorForMethodRetType = repository.getGeneratorForType(jcMethod.returnType)
            val methodValue = generatorForMethodRetType.generate()
            initStmts.addAll(methodValue.initStmts)
            jcMethod.method to listOf(methodValue.instance)
        }
        val mock = UTestMockObject(jcType, fields, methods)
        UTestValueRepresentation(mock, initStmts)
    }
}