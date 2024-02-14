package org.usvm.fuzzer.generator.collections.queue

import org.usvm.fuzzer.generator.collections.list.ListGenerator
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.priorityQueueType

class PriorityQueueGenerator(type: JcTypeWrapper) :
    ListGenerator(
        collectionClass = type.type.classpath.priorityQueueType().jcClass,
        realType = type
    )
