package org.usvm.fuzzer.generator.collections.map

import org.jacodb.api.JcClassType
import org.usvm.fuzzer.generator.collections.list.CollectionGenerator

open class MapGenerator(collectionType: JcClassType) : CollectionGenerator(collectionType, "put")