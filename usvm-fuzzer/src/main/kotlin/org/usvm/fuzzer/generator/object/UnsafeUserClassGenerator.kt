package org.usvm.fuzzer.generator.`object`

import org.jacodb.impl.types.JcClassTypeImpl
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestAllocateMemoryCall
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestSetFieldStatement

class UnsafeUserClassGenerator(private val jcTypeWrapper: JcTypeWrapper) : UserClassGenerator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val jcClass = (jcTypeWrapper.type as JcClassTypeImpl).jcClass
        val fieldInitStmts = mutableListOf<UTestInst>()
        val instance = UTestAllocateMemoryCall(jcClass)
        val fields = jcTypeWrapper.declaredFields
            .filterNot { it.isStatic }
            .map { jcField ->
                val fieldType = jcTypeWrapper.getFieldType(jcField)
                val generatorForField = repository.getGeneratorForType(fieldType)
                val fieldValue = generatorForField.generate()
                fieldInitStmts.addAll(fieldValue.initStmts)
                UTestSetFieldStatement(instance, jcField.field, fieldValue.instance)
            }
        UTestValueRepresentation(instance, fieldInitStmts + fields)
    }
}