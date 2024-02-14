package org.usvm.fuzzer.generator.collections

import org.usvm.fuzzer.generator.collections.list.ListGenerator
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.stackType

class StackGenerator(type: JcTypeWrapper) :
    ListGenerator(
        collectionClass = type.type.classpath.stackType().jcClass,
        realType = type
    )