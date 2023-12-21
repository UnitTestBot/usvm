package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.long
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestLongExpression
import org.usvm.fuzzer.generator.random.nextLong

class LongGenerator(): Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        val randomLong = random.nextLong()
        UTestValueRepresentation(UTestLongExpression(randomLong, jcClasspath.long))
    }
}