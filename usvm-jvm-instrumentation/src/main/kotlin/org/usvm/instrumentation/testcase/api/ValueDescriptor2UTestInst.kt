package org.usvm.instrumentation.testcase.api

import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor

/**
 * Class represents value descriptor with UTestInst, which used for object concrete instantiation
 */
data class ValueDescriptor2UTestInst(
    val valueDescriptor: UTestValueDescriptor?,
    val originUTestInst: UTestInst?
)