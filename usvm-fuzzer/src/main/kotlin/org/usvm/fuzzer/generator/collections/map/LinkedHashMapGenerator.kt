package org.usvm.fuzzer.generator.collections.map

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.linkedHashMapType

class LinkedHashMapGenerator(type: JcTypeWrapper): MapGenerator(type.type.classpath.linkedHashMapType(), type.typeArguments)