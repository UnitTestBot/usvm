package org.usvm.fuzzer.generator.`object`

import org.jacodb.impl.types.JcClassTypeImpl
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestAllocateMemoryCall
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestSetFieldStatement
import org.usvm.instrumentation.util.toJavaField

class UnsafeUserClassGenerator(private val jcTypeWrapper: JcTypeWrapper) : UserClassGenerator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = { depth ->
        val jcClass = (jcTypeWrapper.type as JcClassTypeImpl).jcClass
        val fieldInitStmts = mutableListOf<UTestInst>()
        val instance = UTestAllocateMemoryCall(jcClass)
        val fields = jcTypeWrapper.declaredFields
            .filterNot { it.isStatic }
            .mapNotNull { jcField ->
                val jField = jcField.field.toJavaField(userClassLoader) ?: return@mapNotNull null
                val fieldType = genericGenerator.getFieldType(jcTypeWrapper, jField)
                val generatorForField = repository.getGeneratorForType(fieldType)
                val fieldValue = generatorForField.generate(depth)
                fieldInitStmts.addAll(fieldValue.initStmts)
                UTestSetFieldStatement(instance, jcField.field, fieldValue.instance)
            }
        UTestValueRepresentation(instance, fieldInitStmts + fields)
    }
}