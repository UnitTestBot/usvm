package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.short
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestShortExpression

class ShortGenerator(): Generator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val randomShort = random.nextInt(GeneratorSettings.minShort, GeneratorSettings.maxShort).toShort()
        UTestValueRepresentation(UTestShortExpression(randomShort, jcClasspath.short))
    }
}