package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.float
import org.jacodb.api.ext.long
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestFloatExpression
import org.usvm.fuzzer.generator.random.nextDouble

class FloatGenerator(): Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        val randomFloat =
            if (random.getTrueWithProb(10)) {
                listOf(
                    0.0F,
                    1.0F,
                    -1.0F,
                    Float.MAX_VALUE,
                    Float.MIN_VALUE,
                    Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    Float.NaN
                ).random()
            } else if (random.getTrueWithProb(30)) {
                extractedConstants[jcClasspath.float]?.randomOrNull() as? Float ?: random.nextDouble().toFloat()
            } else  {
                random.nextDouble().toFloat()
            }
        UTestValueRepresentation(UTestFloatExpression(randomFloat, jcClasspath.float))
    }
}