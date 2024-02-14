package org.usvm.fuzzer.generator.collections.set

import org.usvm.fuzzer.generator.collections.list.ListGenerator
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.hashSetType

class HashSetGenerator(type: JcTypeWrapper) :
    ListGenerator(
        collectionClass = type.type.classpath.hashSetType().jcClass,
        realType = type
    )