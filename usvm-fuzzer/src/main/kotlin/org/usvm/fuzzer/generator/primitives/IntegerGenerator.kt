package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.int
import org.usvm.fuzzer.api.UTypedTestIntExpression
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.generator.random.nextInt
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.fuzzer.util.intTypeWrapper
import org.usvm.instrumentation.testcase.api.UTestBooleanExpression
import org.usvm.instrumentation.testcase.api.UTestIntExpression

class IntegerGenerator : Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        val randomInt = if (random.getTrueWithProb(10)) {
            listOf(
                0,
                1,
                -1,
                Int.MAX_VALUE,
                Int.MIN_VALUE
            ).random()
        } else if (random.getTrueWithProb(30)) {
            extractedConstants[jcClasspath.int]?.randomOrNull() as? Int ?: random.nextInt()
        } else {
            random.nextInt()
        }
        UTestValueRepresentation(UTypedTestIntExpression(randomInt, jcClasspath.intTypeWrapper()))
    }
}