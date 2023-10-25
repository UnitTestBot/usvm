package org.usvm.fuzzer.generator

import org.usvm.fuzzer.util.UTestValueRepresentation
import org.usvm.instrumentation.testcase.api.UTestExpression

abstract class Generator {
    lateinit var ctx: GeneratorContext
    protected abstract val generationFun: GeneratorContext.() -> UTestValueRepresentation?

    fun generate() = generationFun.invoke(ctx)!!
}