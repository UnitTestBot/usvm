package org.usvm.fuzzer.generator.collections.set

import org.usvm.fuzzer.generator.collections.list.ListGenerator
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.linkedHashSetType

class LinkedHashSetGenerator(type: JcTypeWrapper) :
    ListGenerator(
        collectionClass = type.type.classpath.linkedHashSetType().jcClass,
        realType = type
    )