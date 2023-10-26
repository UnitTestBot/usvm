package org.usvm.fuzzer.generator.collections.map

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.concurrentHashMapType
import org.usvm.fuzzer.util.hashMapType

class HashMapGenerator(type: JcTypeWrapper): MapGenerator(type.type.classpath.hashMapType(), type.typeArguments) {}