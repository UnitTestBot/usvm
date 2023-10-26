package org.usvm.fuzzer.generator.collections.list

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.arrayListType
import org.usvm.fuzzer.util.linkedHashMapType
import org.usvm.fuzzer.util.linkedHashSetType

class LinkedHashSetGenerator(type: JcTypeWrapper): ListGenerator(type.type.classpath.linkedHashSetType(), type.typeArguments) {}