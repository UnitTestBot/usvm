package org.usvm.instrumentation.serializer

import org.jacodb.api.jvm.JcClasspath
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
import org.usvm.instrumentation.testcase.api.UTestInst
import java.util.*

class SerializationContext(
    val jcClasspath: JcClasspath,
) {
    val serializedUTestInstructions = IdentityHashMap<UTestInst, Int>()
    val deserializedUTestInstructions: MutableMap<Int, UTestInst> = hashMapOf()
    val serializedDescriptors = IdentityHashMap<UTestValueDescriptor, Int>()
    val deserializedDescriptors = HashMap<Int, UTestValueDescriptor>()

    fun reset() {
        serializedUTestInstructions.clear()
        serializedDescriptors.clear()
        deserializedDescriptors.clear()
    }
}