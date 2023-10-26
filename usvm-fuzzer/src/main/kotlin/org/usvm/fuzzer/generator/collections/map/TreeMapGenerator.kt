package org.usvm.fuzzer.generator.collections.map

import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.treeMapType

class TreeMapGenerator(type: JcTypeWrapper): MapGenerator(type.type.classpath.treeMapType(), type.typeArguments)