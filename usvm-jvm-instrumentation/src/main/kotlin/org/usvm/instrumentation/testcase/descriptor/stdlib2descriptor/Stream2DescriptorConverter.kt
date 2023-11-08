package org.usvm.instrumentation.testcase.descriptor.stdlib2descriptor

import org.jacodb.api.JcClasspath
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.descriptor.UTestAdvancedObjectDescriptor
import org.usvm.instrumentation.testcase.descriptor.Value2DescriptorConverter
import java.util.stream.Stream

class Stream2DescriptorConverter: StdlibValue2DescriptorConverter() {
    override fun convert(
        value: Any,
        jcClasspath: JcClasspath,
        parentConverter: Value2DescriptorConverter,
        originUTestInst: UTestInst?
    ): UTestAdvancedObjectDescriptor {
        TODO()
    }

    override fun isPossibleToConvert(value: Any): Boolean {
        TODO("Not yet implemented")
    }
}