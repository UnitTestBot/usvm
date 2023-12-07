package org.usvm.fuzzer.generator.collections.map

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.concurrentHashMapType

class ConcurrentHashMapGenerator(type: JcTypeWrapper):
    MapGenerator(
        collectionClass = type.type.classpath.concurrentHashMapType().jcClass,
        type = type
    )