package org.usvm.instrumentation.testcase.descriptor.stdlib2descriptor

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.usvm.instrumentation.testcase.api.UTestInst
import org.usvm.instrumentation.testcase.descriptor.UTestAdvancedObjectDescriptor
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
import org.usvm.instrumentation.testcase.descriptor.Value2DescriptorConverter

sealed class StdlibValue2DescriptorConverter {

    companion object {
        fun getSpecialConverterForStdLibClass(jClass: Class<*>): StdlibValue2DescriptorConverter? =
            when (jClass) {

                /**
                 * Optionals
                 */
                java.util.OptionalInt::class.java -> Optional2DescriptorConverter()
                java.util.OptionalLong::class.java -> Optional2DescriptorConverter()
                java.util.OptionalDouble::class.java -> Optional2DescriptorConverter()
                java.util.Optional::class.java -> Optional2DescriptorConverter()

                /**
                 * Lists
                 */
                java.util.LinkedList::class.java -> List2DescriptorConverter()
                java.util.ArrayList::class.java -> List2DescriptorConverter()
                java.util.AbstractList::class.java -> List2DescriptorConverter()
                java.util.List::class.java -> List2DescriptorConverter()
                java.util.concurrent.CopyOnWriteArrayList::class.java -> List2DescriptorConverter()

                /**
                 * Queues, deques
                 */
                java.util.PriorityQueue::class.java -> List2DescriptorConverter()
                java.util.ArrayDeque::class.java -> List2DescriptorConverter()
                java.util.concurrent.LinkedBlockingQueue::class.java -> List2DescriptorConverter()
                java.util.concurrent.LinkedBlockingDeque::class.java -> List2DescriptorConverter()
                java.util.concurrent.ConcurrentLinkedQueue::class.java -> List2DescriptorConverter()
                java.util.concurrent.ConcurrentLinkedDeque::class.java -> List2DescriptorConverter()
                java.util.Queue::class.java -> List2DescriptorConverter()
                java.util.Deque::class.java -> List2DescriptorConverter()

                /**
                 * Sets
                 */
                java.util.HashSet::class.java -> List2DescriptorConverter()
                java.util.TreeSet::class.java -> List2DescriptorConverter()
                java.util.LinkedHashSet::class.java -> List2DescriptorConverter()
                java.util.AbstractSet::class.java -> List2DescriptorConverter()
                java.util.Set::class.java -> List2DescriptorConverter()

                /**
                 * Maps
                 */
                java.util.HashMap::class.java -> Map2DescriptorConverter()
                java.util.TreeMap::class.java -> Map2DescriptorConverter()
                java.util.LinkedHashMap::class.java -> Map2DescriptorConverter()
                java.util.AbstractMap::class.java -> Map2DescriptorConverter()
                java.util.concurrent.ConcurrentMap::class.java -> Map2DescriptorConverter()
                java.util.concurrent.ConcurrentHashMap::class.java -> Map2DescriptorConverter()
                java.util.IdentityHashMap::class.java -> Map2DescriptorConverter()
                java.util.WeakHashMap::class.java -> Map2DescriptorConverter()

                /**
                 * Hashtables
                 */
                java.util.Hashtable::class.java -> Map2DescriptorConverter()

                else -> null
            }
    }

    abstract fun convert(
        value: Any,
        jcClasspath: JcClasspath,
        parentConverter: Value2DescriptorConverter,
        originUTestInst: UTestInst?
    ): UTestAdvancedObjectDescriptor

    abstract fun isPossibleToConvert(value: Any): Boolean

    fun createDescriptor(
        jcClasspath: JcClasspath,
        jcClass: JcClassOrInterface,
        originUTestInst: UTestInst?,
        value: Any
    ): Pair<MutableList<Pair<JcMethod, List<UTestValueDescriptor>>>, UTestAdvancedObjectDescriptor> {
        val instantiationChain = mutableListOf<Pair<JcMethod, List<UTestValueDescriptor>>>()
        return instantiationChain to UTestAdvancedObjectDescriptor(
            jcClasspath.typeOf(jcClass),
            instantiationChain,
            originUTestInst,
            System.identityHashCode(value)
        )
    }
}