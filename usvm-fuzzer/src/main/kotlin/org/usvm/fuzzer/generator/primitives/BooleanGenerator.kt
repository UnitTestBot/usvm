package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.boolean
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestBooleanExpression

class BooleanGenerator: Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        UTestValueRepresentation(UTestBooleanExpression(random.nextBoolean(), jcClasspath.boolean))
    }
}