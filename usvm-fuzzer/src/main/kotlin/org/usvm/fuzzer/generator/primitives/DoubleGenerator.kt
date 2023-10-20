package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.double
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestDoubleExpression

class DoubleGenerator(): Generator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val randomDouble = random.nextDouble(GeneratorSettings.minDouble, GeneratorSettings.maxDouble)
        UTestValueRepresentation(UTestDoubleExpression(randomDouble, jcClasspath.double))
    }
}