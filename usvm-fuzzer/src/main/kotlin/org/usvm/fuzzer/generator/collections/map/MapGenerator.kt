package org.usvm.fuzzer.generator.collections.map

import org.jacodb.api.JcClassType
import org.jacodb.api.JcType
import org.usvm.fuzzer.generator.collections.list.CollectionGenerator
import org.usvm.fuzzer.types.JcTypeWrapper

open class MapGenerator(collectionType: JcClassType, componentTypes: List<JcTypeWrapper>) :
    CollectionGenerator(collectionType, componentTypes, "put")