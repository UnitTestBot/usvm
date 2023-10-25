package org.usvm.fuzzer.generator.collections.list

import org.jacodb.api.JcClassType

sealed class ListGenerator(listType: JcClassType): CollectionGenerator(listType, "add")