package org.usvm.fuzzer.generator.collections.map

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.linkedHashMapType

class LinkedHashMapGenerator(type: JcTypeWrapper):
    MapGenerator(
        collectionClass = type.type.classpath.linkedHashMapType().jcClass,
        type = type
    )