package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.double
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestDoubleExpression
import org.usvm.fuzzer.generator.random.nextDouble

class DoubleGenerator() : Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        if (random.getTrueWithProb(10)) {
            val randomFromPredefinedBoundDoubles = listOf(
                0.0,
                1.0,
                Double.MAX_VALUE,
                Double.MIN_VALUE,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
            ).random()
            UTestValueRepresentation(UTestDoubleExpression(randomFromPredefinedBoundDoubles, jcClasspath.double))
        } else {
            val randomDouble = random.nextDouble(GeneratorSettings.minDouble, GeneratorSettings.maxDouble)
            UTestValueRepresentation(UTestDoubleExpression(randomDouble, jcClasspath.double))
        }
    }
}