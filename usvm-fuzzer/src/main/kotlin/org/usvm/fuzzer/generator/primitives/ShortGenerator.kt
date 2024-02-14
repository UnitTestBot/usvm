package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.long
import org.jacodb.api.ext.short
import org.usvm.fuzzer.api.UTypedTestShortExpression
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.generator.random.nextInt
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.fuzzer.util.shortTypeWrapper
import org.usvm.instrumentation.testcase.api.UTestShortExpression

class ShortGenerator(): Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        val randomShort =
            if (random.getTrueWithProb(10)) {
                listOf(
                    0,
                    1,
                    -1,
                    Short.MAX_VALUE,
                    Short.MIN_VALUE
                ).random()
            } else if (random.getTrueWithProb(30)) {
                extractedConstants[jcClasspath.short]?.randomOrNull() as? Short ?: random.nextLong().toShort()
            } else  {
                random.nextLong().toShort()
            }
        UTestValueRepresentation(UTypedTestShortExpression(randomShort, jcClasspath.shortTypeWrapper()))
    }
}