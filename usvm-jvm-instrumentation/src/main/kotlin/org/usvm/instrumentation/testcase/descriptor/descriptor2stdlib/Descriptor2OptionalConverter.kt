package org.usvm.instrumentation.testcase.descriptor.descriptor2stdlib

import org.usvm.instrumentation.testcase.descriptor.Descriptor2ValueConverter
import org.usvm.instrumentation.testcase.descriptor.UTestAdvancedObjectDescriptor
import java.util.*

class Descriptor2OptionalConverter: Descriptor2StdlibValueConverter() {
    override fun convert(
        descriptor: UTestAdvancedObjectDescriptor,
        jClass: Class<*>,
        parentConverter: Descriptor2ValueConverter
    ): Any =
        parentConverter.buildObjectFromDescriptor(descriptor.instantiationChain.first().second.first())?.let {
            Optional.of(it)
        } ?: Optional.ofNullable(null)

}