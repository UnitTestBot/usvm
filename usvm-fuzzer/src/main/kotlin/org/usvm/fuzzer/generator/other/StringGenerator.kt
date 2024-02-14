package org.usvm.fuzzer.generator.other

import org.usvm.fuzzer.api.UTypedTestStringExpression
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorSettings
import org.usvm.fuzzer.generator.random.nextInt
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.fuzzer.util.stringTypeWrapper
import org.usvm.instrumentation.testcase.api.UTestStringExpression
import org.usvm.instrumentation.util.stringType

class StringGenerator: Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        val length = random.nextInt(GeneratorSettings.minStringLength, GeneratorSettings.maxStringLength)
        val randomString = buildString {
            repeat(length) {
                append(GeneratorSettings.stringAvailableSymbols.random())
            }
        }
        UTestValueRepresentation(UTypedTestStringExpression(randomString, jcClasspath.stringTypeWrapper()))
    }
}