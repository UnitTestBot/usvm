package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.float
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestFloatExpression
import org.usvm.fuzzer.generator.random.nextDouble

class FloatGenerator(): Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        val randomFloat = random.nextDouble().toFloat()
        UTestValueRepresentation(UTestFloatExpression(randomFloat, jcClasspath.float))
    }
}