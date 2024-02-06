package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.int
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.getTrueWithProb
import org.usvm.fuzzer.generator.random.nextInt
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestByteExpression

class ByteGenerator: Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        val randomByte = if (random.getTrueWithProb(10)) {
            listOf(
                0,
                1,
                -1,
                Byte.MAX_VALUE,
                Byte.MIN_VALUE
            ).random()
        } else if (random.getTrueWithProb(30)) {
            extractedConstants[jcClasspath.byte]?.randomOrNull() as? Byte ?: random.nextInt().toByte()
        } else {
            random.nextInt().toByte()
        }
        UTestValueRepresentation(UTestByteExpression(randomByte, jcClasspath.byte))
    }
}