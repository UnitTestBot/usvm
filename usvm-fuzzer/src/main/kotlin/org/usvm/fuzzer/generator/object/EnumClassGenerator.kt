package org.usvm.fuzzer.generator.`object`

import org.jacodb.api.JcClassType
import org.jacodb.api.ext.enumValues
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestGetStaticFieldExpression

class EnumClassGenerator(private val jcType: JcTypeWrapper) : UserClassGenerator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation? = {
        val jcClass = (jcType.type as JcClassType).jcClass
        jcClass.enumValues?.random()?.let {
            UTestValueRepresentation(UTestGetStaticFieldExpression(it))
        }
    }
}