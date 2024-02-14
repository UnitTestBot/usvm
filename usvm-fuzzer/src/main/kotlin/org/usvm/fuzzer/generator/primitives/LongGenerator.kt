package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.long
import org.usvm.fuzzer.api.UTypedTestLongExpression
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestLongExpression
import org.usvm.fuzzer.generator.random.nextLong
import org.usvm.fuzzer.util.longTypeWrapper

class LongGenerator(): Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        val randomLong =
            if (random.getTrueWithProb(10)) {
                listOf(
                    0L,
                    1L,
                    -1L,
                    Long.MAX_VALUE,
                    Long.MIN_VALUE
                ).random()
            } else if (random.getTrueWithProb(30)) {
                extractedConstants[jcClasspath.long]?.randomOrNull() as? Long ?: random.nextLong()
            } else  {
                random.nextLong()
            }
        UTestValueRepresentation(UTypedTestLongExpression(randomLong, jcClasspath.longTypeWrapper()))
    }
}