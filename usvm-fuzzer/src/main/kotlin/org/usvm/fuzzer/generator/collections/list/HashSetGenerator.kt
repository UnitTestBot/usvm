package org.usvm.fuzzer.generator.collections.list

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.arrayListType
import org.usvm.fuzzer.util.hashSetType

class HashSetGenerator(type: JcTypeWrapper) :
    ListGenerator(
        collectionClass = type.type.classpath.hashSetType().jcClass,
        realType = type
    )