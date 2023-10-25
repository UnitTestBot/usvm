package org.usvm.fuzzer.generator.`object`

import org.jacodb.api.JcClassType
import org.jacodb.api.JcType
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestAllocateMemoryCall
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestSetFieldStatement
import org.usvm.instrumentation.util.toJcClass

class UnsafeUserClassGenerator(private val jcType: JcClassType) : UserClassGenerator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val jcClass = jcType.jcClass
        val fieldInitStmts = mutableListOf<UTestInst>()
        val instance = UTestAllocateMemoryCall(jcClass)
        val fields = jcType.declaredFields
            .filterNot { it.isStatic }
            .map { jcField ->
                val generatorForField = repository.getGeneratorForType(jcField.fieldType)
                val fieldValue = generatorForField.generate()
                fieldInitStmts.addAll(fieldValue.initStmts)
                UTestSetFieldStatement(instance, jcField.field, fieldValue.instance)
            }
        UTestValueRepresentation(instance, fieldInitStmts + fields)
    }
}