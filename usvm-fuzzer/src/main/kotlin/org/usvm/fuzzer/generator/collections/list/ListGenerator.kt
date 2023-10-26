package org.usvm.fuzzer.generator.collections.list

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.types.JcTypeWrapper

sealed class ListGenerator(collectionType: JcClassType, componentTypes: List<JcTypeWrapper>) :
    CollectionGenerator(collectionType, componentTypes, "add")