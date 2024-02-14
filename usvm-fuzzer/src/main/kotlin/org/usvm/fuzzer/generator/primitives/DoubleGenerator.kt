package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.double
import org.jacodb.api.ext.float
import org.usvm.fuzzer.api.UTypedTestDoubleExpression
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestDoubleExpression
import org.usvm.fuzzer.generator.random.nextDouble
import org.usvm.fuzzer.util.doubleTypeWrapper

class DoubleGenerator() : Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        val randomDouble = if (random.getTrueWithProb(10)) {
            listOf(
                0.0,
                1.0,
                Double.MAX_VALUE,
                Double.MIN_VALUE,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY
            ).random()
        } else if (random.getTrueWithProb(30)) {
            extractedConstants[jcClasspath.double]?.randomOrNull() as? Double ?: random.nextDouble(GeneratorSettings.minDouble, GeneratorSettings.maxDouble)
        } else {
            random.nextDouble(GeneratorSettings.minDouble, GeneratorSettings.maxDouble)
        }
        UTestValueRepresentation(UTypedTestDoubleExpression(randomDouble, jcClasspath.doubleTypeWrapper()))
    }
}