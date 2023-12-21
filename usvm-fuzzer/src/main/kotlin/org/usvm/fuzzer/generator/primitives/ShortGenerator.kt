package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.short
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.nextInt
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestShortExpression

class ShortGenerator(): Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        val randomShort = random.nextInt().toShort()
        UTestValueRepresentation(UTestShortExpression(randomShort, jcClasspath.short))
    }
}