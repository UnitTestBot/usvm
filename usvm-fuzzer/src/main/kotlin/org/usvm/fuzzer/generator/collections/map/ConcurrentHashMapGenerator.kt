package org.usvm.fuzzer.generator.collections.map

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.concurrentHashMapType

class ConcurrentHashMapGenerator(type: JcTypeWrapper):
    MapGenerator(type.type.classpath.concurrentHashMapType(), type.typeArguments) {}