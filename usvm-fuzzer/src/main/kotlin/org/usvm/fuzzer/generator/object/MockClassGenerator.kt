package org.usvm.fuzzer.generator.`object`

import org.jacodb.api.JcClassType
import org.jacodb.api.ext.void
import org.usvm.fuzzer.api.UTypedTestInst
import org.usvm.fuzzer.api.UTypedTestMockObject
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.api.UTestMockObject
import org.usvm.instrumentation.util.toJavaField
import org.usvm.instrumentation.util.toJavaMethod

class MockClassGenerator(private val jcType: JcTypeWrapper): UserClassGenerator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = { depth ->
        val initStmts = mutableListOf<UTypedTestInst>()
        val fields = jcType.declaredFields.mapNotNull { jcField ->
            val jField = jcField.field.toJavaField(userClassLoader) ?: return@mapNotNull null
            val fieldType = genericGenerator.getFieldType(jcType, jField)
            val generatorForField = repository.getGeneratorForType(fieldType)
            val fieldValue = generatorForField.generate(depth)
            initStmts.addAll(fieldValue.initStmts)
            jcField.field to fieldValue.instance
        }.toMap()
        val methods = jcType.declaredMethods.mapNotNull { jcMethod ->
            val jMethod =
                try {
                    jcMethod.method.toJavaMethod(userClassLoader)
                } catch (e: Throwable) {
                    return@mapNotNull null
                }
            val methodReturnType = genericGenerator.resolveMethodReturnType(jcType, jMethod)
            if (methodReturnType.type == jcClasspath.void) {
                return@mapNotNull null
            }
            val generatorForMethodRetType = repository.getGeneratorForType(methodReturnType)
            val methodValue = generatorForMethodRetType.generate(depth)
            initStmts.addAll(methodValue.initStmts)
            jcMethod.method to listOf(methodValue.instance)
        }.toMap().filterValues { it.isNotEmpty() }
        val mock = UTypedTestMockObject(jcType, fields, methods)
        UTestValueRepresentation(mock, initStmts)
    }
}