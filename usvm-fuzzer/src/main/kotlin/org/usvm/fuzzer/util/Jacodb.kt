package org.usvm.fuzzer.util

import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcType
import org.jacodb.api.ext.toType
import org.usvm.fuzzer.types.JcType2JvmTypeConverter
import org.usvm.fuzzer.types.JcTypeWrapper

fun JcClasspath.findResolvedTypeOrNull(name: String): JcTypeWrapper =
    JcType2JvmTypeConverter.convertToJcTypeWrapper(name, this)

fun JcClasspath.arrayListType(): JcClassType {
    return findTypeOrNull("java.util.ArrayList") as JcClassType
}

fun JcClasspath.linkedListType(): JcClassType {
    return findTypeOrNull("java.util.LinkedList") as JcClassType
}

fun JcClasspath.listType(): JcClassType {
    return findTypeOrNull("java.util.List") as JcClassType
}

fun JcClasspath.mapType(): JcClassType {
    return findTypeOrNull("java.util.Map") as JcClassType
}

fun JcClasspath.setType(): JcClassType {
    return findTypeOrNull("java.util.Set") as JcClassType
}
fun JcClasspath.linkedHashSetType(): JcClassType {
    return findTypeOrNull("java.util.LinkedHashSet") as JcClassType
}

fun JcClasspath.dequeType(): JcClassType {
    return findTypeOrNull("java.util.Deque") as JcClassType
}

fun JcClasspath.queueType(): JcClassType {
    return findTypeOrNull("java.util.Queue") as JcClassType
}

fun JcClasspath.hashSetType(): JcClassType {
    return findTypeOrNull("java.util.HashSet") as JcClassType
}

fun JcClasspath.treeSetType(): JcClassType {
    return findTypeOrNull("java.util.TreeSet") as JcClassType
}

fun JcClasspath.hashMapType(): JcClassType {
    return findTypeOrNull("java.util.HashMap") as JcClassType
}

fun JcClasspath.concurrentHashMapType(): JcClassType {
    return findTypeOrNull("java.util.concurrent.ConcurrentHashMap") as JcClassType
}


fun JcClasspath.treeMapType(): JcClassType {
    return findTypeOrNull("java.util.TreeMap") as JcClassType
}

fun JcClasspath.linkedHashMapType(): JcClassType {
    return findTypeOrNull("java.util.LinkedHashMap") as JcClassType
}

fun JcClasspath.arrayDequeType(): JcClassType {
    return findTypeOrNull("java.util.ArrayDeque") as JcClassType
}

fun JcClasspath.priorityQueueType(): JcClassType {
    return findTypeOrNull("java.util.PriorityQueue") as JcClassType
}

fun JcClasspath.stackType(): JcClassType {
    return findTypeOrNull("java.util.Stack") as JcClassType
}