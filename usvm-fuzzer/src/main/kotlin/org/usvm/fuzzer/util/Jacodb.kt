package org.usvm.fuzzer.util

import org.jacodb.api.*
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.autoboxIfNeeded
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.toType
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.instrumentation.util.toJavaClass
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet

//import org.usvm.fuzzer.types.JcType2JvmTypeConverter
//import org.usvm.fuzzer.types.JcTypeWrapper

//fun JcClasspath.findResolvedTypeOrNull(name: String): JcTypeWrapper =
//    JcType2JvmTypeConverter.convertToJcTypeWrapper(name, this)

fun JcClasspath.arrayListType(): JcClassType {
    return findTypeOrNull<ArrayList<*>>() as JcClassType
}

fun JcClasspath.classType(): JcClassType {
    return findTypeOrNull<Class<*>>() as JcClassType
}

fun JcClasspath.linkedListType(): JcClassType {
    return findTypeOrNull<LinkedList<*>>() as JcClassType
}

fun JcClasspath.listType(): JcClassType {
    return findTypeOrNull<List<*>>() as JcClassType
}

fun JcClasspath.collectionType(): JcClassType {
    return findTypeOrNull<java.util.Collection<*>>() as JcClassType
}

fun JcClasspath.iterableType(): JcClassType {
    return findTypeOrNull<java.lang.Iterable<*>>() as JcClassType
}

fun JcClasspath.mapType(): JcClassType {
    return findTypeOrNull<Map<*, *>>() as JcClassType
}

fun JcClasspath.setType(): JcClassType {
    return findTypeOrNull<Set<*>>() as JcClassType
}
fun JcClasspath.linkedHashSetType(): JcClassType {
    return findTypeOrNull<java.util.LinkedHashSet<*>>() as JcClassType
}

fun JcClasspath.dequeType(): JcClassType {
    return findTypeOrNull<Deque<*>>() as JcClassType
}

fun JcClasspath.queueType(): JcClassType {
    return findTypeOrNull<Queue<*>>() as JcClassType
}

fun JcClasspath.hashSetType(): JcClassType {
    return findTypeOrNull<HashSet<*>>() as JcClassType
}

fun JcClasspath.treeSetType(): JcClassType {
    return findTypeOrNull<TreeSet<*>>() as JcClassType
}

fun JcClasspath.navigableSetType(): JcClassType {
    return findTypeOrNull<NavigableSet<*>>() as JcClassType
}

fun JcClasspath.sortedSetType(): JcClassType {
    return findTypeOrNull<SortedSet<*>>() as JcClassType
}

fun JcClasspath.hashMapType(): JcClassType {
    return findTypeOrNull<HashMap<*, *>>() as JcClassType
}

fun JcClasspath.concurrentHashMapType(): JcClassType {
    return findTypeOrNull<ConcurrentHashMap<*, *>>() as JcClassType
}


fun JcClasspath.treeMapType(): JcClassType {
    return findTypeOrNull<TreeMap<*, *>>() as JcClassType
}

fun JcClasspath.linkedHashMapType(): JcClassType {
    return findTypeOrNull<LinkedHashMap<*, *>>() as JcClassType
}

fun JcClasspath.arrayDequeType(): JcClassType {
    return findTypeOrNull<ArrayDeque<*>>() as JcClassType
}

fun JcClasspath.priorityQueueType(): JcClassType {
    return findTypeOrNull<PriorityQueue<*>>() as JcClassType
}

fun JcClasspath.stackType(): JcClassType {
    return findTypeOrNull<Stack<*>>() as JcClassType
}

fun JcClassOrInterface.createJcTypeWrapper(userClassLoader: ClassLoader): JcTypeWrapper = toType().createJcTypeWrapper(userClassLoader)

fun JcType.createJcTypeWrapper(userClassLoader: ClassLoader): JcTypeWrapper =
    JcTypeWrapper(this, toJavaClass(userClassLoader))

fun JcIfInst.getTrueBranchInst(): JcInst =
    location.method.instList[trueBranch.index]

fun JcIfInst.getFalseBranchInst(): JcInst =
    location.method.instList[falseBranch.index]

fun JcField.findGetter(): JcMethod? {
    val cp = enclosingClass.classpath
    return enclosingClass.declaredMethods.find { jcMethod ->
        if (type == cp.boolean || type == cp.boolean.autoboxIfNeeded()) {
            jcMethod.name.equals("is${name}", true) ||
                    jcMethod.name.equals("get${name}", true)
        } else {
            jcMethod.name.equals("get${name}", true)
        } && jcMethod.parameters.isEmpty()
    }
}

fun JcField.findSetter(): JcMethod? =
    enclosingClass.declaredMethods.find { jcMethod ->
        jcMethod.name.equals("set${name}", true)
    }

val JcClassOrInterface.isFunctionalInterface
    get() = isInterface && annotations.any { it.name == "java.lang.FunctionalInterface" }
