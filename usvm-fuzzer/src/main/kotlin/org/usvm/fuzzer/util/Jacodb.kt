package org.usvm.fuzzer.util

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcType
import org.jacodb.api.ext.toType

fun JcClasspath.arrayListType(): JcType {
    return findTypeOrNull("java.util.ArrayList")!!
}

fun JcClasspath.linkedListType(): JcType {
    return findTypeOrNull("java.util.LinkedList")!!
}

fun JcClasspath.listType(): JcType {
    return findTypeOrNull("java.util.List")!!
}

fun JcClasspath.mapType(): JcType {
    return findTypeOrNull("java.util.Map")!!
}

fun JcClasspath.setType(): JcType {
    return findTypeOrNull("java.util.Set")!!
}

fun JcClasspath.dequeType(): JcType {
    return findTypeOrNull("java.util.Deque")!!
}
fun JcClasspath.queueType(): JcType {
    return findTypeOrNull("java.util.Queue")!!
}

fun JcClasspath.hashSetType(): JcType {
    return findTypeOrNull("java.util.HashSet")!!
}

fun JcClasspath.treeSetType(): JcType {
    return findTypeOrNull("java.util.TreeSet")!!
}

fun JcClasspath.hashMapType(): JcType {
    return findTypeOrNull("java.util.HashMap")!!
}

fun JcClasspath.treeMapType(): JcType {
    return findTypeOrNull("java.util.TreeMap")!!
}

fun JcClasspath.linkedHashMapType(): JcType {
    return findTypeOrNull("java.util.LinkedHashMap")!!
}

fun JcClasspath.arrayDequeType(): JcType {
    return findTypeOrNull("java.util.ArrayDeque")!!
}

fun JcClasspath.priorityQueueType(): JcType {
    return findTypeOrNull("java.util.PriorityQueue")!!
}

fun JcClasspath.stackType(): JcType {
    return findTypeOrNull("java.util.Stack")!!
}