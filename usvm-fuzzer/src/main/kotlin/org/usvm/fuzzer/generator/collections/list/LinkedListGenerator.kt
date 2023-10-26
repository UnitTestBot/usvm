package org.usvm.fuzzer.generator.collections.list

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.linkedHashSetType
import org.usvm.fuzzer.util.linkedListType

class LinkedListGenerator(type: JcTypeWrapper): ListGenerator(type.type.classpath.linkedListType(), type.typeArguments) {}