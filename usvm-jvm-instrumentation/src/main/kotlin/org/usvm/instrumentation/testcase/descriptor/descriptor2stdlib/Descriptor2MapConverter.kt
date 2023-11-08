package org.usvm.instrumentation.testcase.descriptor.descriptor2stdlib

import org.usvm.instrumentation.testcase.descriptor.Descriptor2ValueConverter
import org.usvm.instrumentation.testcase.descriptor.UTestAdvancedObjectDescriptor

class Descriptor2MapConverter: Descriptor2StdlibValueConverter() {

    override fun convert(
        descriptor: UTestAdvancedObjectDescriptor,
        jClass: Class<*>,
        parentConverter: Descriptor2ValueConverter,
    ): Any {
        val instance = when (jClass) {
            java.util.HashMap::class.java -> java.util.HashMap<Any, Any>()
            java.util.TreeMap::class.java -> java.util.TreeMap()
            java.util.LinkedHashMap::class.java -> java.util.LinkedHashMap()
            java.util.AbstractMap::class.java -> java.util.HashMap()
            java.util.concurrent.ConcurrentMap::class.java -> java.util.concurrent.ConcurrentHashMap()
            java.util.concurrent.ConcurrentHashMap::class.java -> java.util.concurrent.ConcurrentHashMap()
            java.util.IdentityHashMap::class.java -> java.util.IdentityHashMap()
            java.util.WeakHashMap::class.java ->  java.util.WeakHashMap()
            else -> error("")
        }
        parentConverter.descriptorToObject[descriptor] = instance
        descriptor.instantiationChain.forEach { (_, args) ->
            val concreteArgs =
                args.map { parentConverter.buildObjectFromDescriptor(it) }
            instance[concreteArgs.first()] = concreteArgs.last()
        }
        return instance
    }
}