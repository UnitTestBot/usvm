package org.usvm.instrumentation.serializer

import org.jacodb.api.JcClasspath
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
import org.usvm.instrumentation.testcase.api.UTestExpression
import java.util.*

class SerializationContext(
    val jcClasspath: JcClasspath,
) {
    val serializedUTestExpressions = IdentityHashMap<UTestExpression, Int>()
    val deserializerCache: MutableMap<Int, UTestExpression> = hashMapOf()
    val serializedDescriptors = IdentityHashMap<UTestValueDescriptor, Int>()
    val deserializedDescriptors = HashMap<Int, UTestValueDescriptor>()

    fun reset() {
        serializedUTestExpressions.clear()
        serializedDescriptors.clear()
        deserializerCache.clear()
        deserializedDescriptors.clear()
    }
}