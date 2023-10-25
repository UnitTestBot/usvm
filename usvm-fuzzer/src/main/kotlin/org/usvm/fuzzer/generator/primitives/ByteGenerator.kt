package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestByteExpression

class ByteGenerator: Generator() {
    override val generationFun: GeneratorContext.() -> UTestValueRepresentation = {
        val randomByte = random.nextInt(GeneratorSettings.minByte.toInt(), GeneratorSettings.maxByte.toInt()).toByte()
        UTestValueRepresentation(UTestByteExpression(randomByte, jcClasspath.byte))
    }
}