package org.usvm.fuzzer.generator.collections.deque

import org.usvm.fuzzer.generator.collections.list.ListGenerator
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.arrayDequeType

class ArrayDequeGenerator(type: JcTypeWrapper) :
    ListGenerator(
        collectionClass = type.type.classpath.arrayDequeType().jcClass,
        realType = type
    )