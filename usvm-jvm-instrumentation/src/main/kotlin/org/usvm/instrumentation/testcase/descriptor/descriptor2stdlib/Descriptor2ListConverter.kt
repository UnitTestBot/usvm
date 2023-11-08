package org.usvm.instrumentation.testcase.descriptor.descriptor2stdlib

import org.jacodb.api.JcClasspath
import org.usvm.instrumentation.testcase.descriptor.Descriptor2ValueConverter
import org.usvm.instrumentation.testcase.descriptor.UTestAdvancedObjectDescriptor
import org.usvm.instrumentation.util.toJavaClass


class Descriptor2ListConverter: Descriptor2StdlibValueConverter() {

    override fun convert(
        descriptor: UTestAdvancedObjectDescriptor,
        jClass: Class<*>,
        parentConverter: Descriptor2ValueConverter,
    ): Any {
        val instance = when (jClass) {
            java.util.LinkedList::class.java -> java.util.LinkedList<Any?>()
            java.util.ArrayList::class.java -> java.util.ArrayList<Any?>()
            java.util.AbstractList::class.java -> java.util.ArrayList<Any?>()
            java.util.List::class.java -> java.util.ArrayList<Any?>()
            java.util.concurrent.CopyOnWriteArrayList::class.java -> java.util.concurrent.CopyOnWriteArrayList()
            else -> error("")
        }
        parentConverter.descriptorToObject[descriptor] = instance
        descriptor.instantiationChain.forEach { (_, args) ->
            val concreteArgs =
                args.map { parentConverter.buildObjectFromDescriptor(it) }
            instance.add(concreteArgs.first())
        }
        return instance
    }
}