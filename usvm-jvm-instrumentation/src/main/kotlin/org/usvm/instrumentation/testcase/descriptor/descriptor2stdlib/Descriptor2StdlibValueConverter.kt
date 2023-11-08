package org.usvm.instrumentation.testcase.descriptor.descriptor2stdlib

import org.jacodb.api.JcClasspath
import org.usvm.instrumentation.testcase.descriptor.Descriptor2ValueConverter
import org.usvm.instrumentation.testcase.descriptor.UTestAdvancedObjectDescriptor

sealed class Descriptor2StdlibValueConverter {
    companion object {
        fun getSpecialConverterForStdLibClass(jClass: Class<*>): Descriptor2StdlibValueConverter? =
            when (jClass) {

                /**
                 * Optionals
                 */
                java.util.OptionalInt::class.java -> Descriptor2OptionalConverter()
                java.util.OptionalLong::class.java -> Descriptor2OptionalConverter()
                java.util.OptionalDouble::class.java -> Descriptor2OptionalConverter()
                java.util.Optional::class.java -> Descriptor2OptionalConverter()

                /**
                 * Lists
                 */
                java.util.LinkedList::class.java -> Descriptor2ListConverter()
                java.util.ArrayList::class.java -> Descriptor2ListConverter()
                java.util.AbstractList::class.java -> Descriptor2ListConverter()
                java.util.List::class.java -> Descriptor2ListConverter()
                java.util.concurrent.CopyOnWriteArrayList::class.java -> Descriptor2ListConverter()

                /**
                 * Queues, deques
                 */
                java.util.PriorityQueue::class.java -> Descriptor2ListConverter()
                java.util.ArrayDeque::class.java -> Descriptor2ListConverter()
                java.util.concurrent.LinkedBlockingQueue::class.java -> Descriptor2ListConverter()
                java.util.concurrent.LinkedBlockingDeque::class.java -> Descriptor2ListConverter()
                java.util.concurrent.ConcurrentLinkedQueue::class.java -> Descriptor2ListConverter()
                java.util.concurrent.ConcurrentLinkedDeque::class.java -> Descriptor2ListConverter()
                java.util.Queue::class.java -> Descriptor2ListConverter()
                java.util.Deque::class.java -> Descriptor2ListConverter()

                /**
                 * Sets
                 */
                java.util.HashSet::class.java -> Descriptor2ListConverter()
                java.util.TreeSet::class.java -> Descriptor2ListConverter()
                java.util.LinkedHashSet::class.java -> Descriptor2ListConverter()
                java.util.AbstractSet::class.java -> Descriptor2ListConverter()
                java.util.Set::class.java -> Descriptor2ListConverter()

                /**
                 * Maps
                 */
                java.util.HashMap::class.java -> Descriptor2MapConverter()
                java.util.TreeMap::class.java -> Descriptor2MapConverter()
                java.util.LinkedHashMap::class.java -> Descriptor2MapConverter()
                java.util.AbstractMap::class.java -> Descriptor2MapConverter()
                java.util.concurrent.ConcurrentMap::class.java -> Descriptor2MapConverter()
                java.util.concurrent.ConcurrentHashMap::class.java -> Descriptor2MapConverter()
                java.util.IdentityHashMap::class.java -> Descriptor2MapConverter()
                java.util.WeakHashMap::class.java -> Descriptor2MapConverter()

                /**
                 * Hashtables
                 */
                java.util.Hashtable::class.java -> Descriptor2MapConverter()
                else -> null
            }
    }

    abstract fun convert(
        descriptor: UTestAdvancedObjectDescriptor,
        jClass: Class<*>,
        parentConverter: Descriptor2ValueConverter,
    ): Any

}