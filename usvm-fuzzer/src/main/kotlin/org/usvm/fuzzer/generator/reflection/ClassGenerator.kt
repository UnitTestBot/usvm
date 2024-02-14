package org.usvm.fuzzer.generator.reflection

import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.objectType
import org.usvm.fuzzer.api.UTypedTestClassExpression
import org.usvm.fuzzer.generator.Generator
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.fuzzer.util.objectTypeWrapper
import org.usvm.instrumentation.testcase.api.UTestBooleanExpression
import org.usvm.instrumentation.testcase.api.UTestClassExpression

class ClassGenerator: Generator() {
    override val generationFun: GeneratorContext.(Int) -> UTestValueRepresentation = {
        UTestValueRepresentation(UTypedTestClassExpression(jcClasspath.objectTypeWrapper()))
    }
}