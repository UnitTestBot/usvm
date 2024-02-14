package org.usvm.fuzzer.generator.primitives

import org.jacodb.api.ext.boolean
import org.usvm.fuzzer.api.UTypedTestBooleanExpression
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.fuzzer.util.booleanTypeWrapper
import org.usvm.instrumentation.testcase.api.UTestBooleanExpression

class BooleanGenerator: Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        UTestValueRepresentation(UTypedTestBooleanExpression(random.nextBoolean(), jcClasspath.booleanTypeWrapper()))
    }
}