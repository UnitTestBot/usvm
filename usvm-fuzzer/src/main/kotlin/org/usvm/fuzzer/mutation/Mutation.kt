package org.usvm.fuzzer.mutation

import org.jacodb.api.JcField
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.strategy.Selectable
import org.usvm.fuzzer.util.FuzzingContext
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor

abstract class Mutation: Selectable() {

    abstract fun mutate(seed: Seed, position: Int): Seed?

}