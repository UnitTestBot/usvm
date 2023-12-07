package org.usvm.fuzzer.generator.collections.list

import org.jacodb.api.JcClassOrInterface
import org.usvm.fuzzer.generator.collections.CollectionGenerator
import org.usvm.fuzzer.types.JcTypeWrapper

sealed class ListGenerator(
    collectionClass: JcClassOrInterface,
    realType: JcTypeWrapper,
) :
    CollectionGenerator(
        collectionClass = collectionClass,
        realType = realType,
        functionNameForAdd = "add"
    )