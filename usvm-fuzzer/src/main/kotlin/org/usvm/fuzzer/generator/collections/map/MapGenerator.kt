package org.usvm.fuzzer.generator.collections.map

import org.jacodb.api.JcClassOrInterface
import org.usvm.fuzzer.generator.collections.CollectionGenerator
import org.usvm.fuzzer.types.JcTypeWrapper

open class MapGenerator(collectionClass: JcClassOrInterface, type: JcTypeWrapper) :
    CollectionGenerator(
        collectionClass = collectionClass,
        realType = type,
        functionNameForAdd = "put"
    )