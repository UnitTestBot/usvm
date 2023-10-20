package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.boolean
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestBooleanExpression
import org.usvm.instrumentation.testcase.api.UTestIntExpression

class IntegerGenerator: Generator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val randomInt = random.nextInt(GeneratorSettings.minInt, GeneratorSettings.maxInt)
        UTestValueRepresentation(UTestIntExpression(randomInt, jcClasspath.boolean))
    }
}