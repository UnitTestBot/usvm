package org.usvm.samples

import org.jacodb.go.api.NamedType
import org.usvm.interpreter.GoInterfaceValue

fun GoMap.hasLongKey(key: Long): Boolean = orEmpty().keys.any { it.longValue() == key }
fun GoInterfaceValue.isNamed(name: String): Boolean = (type as? NamedType)?.name == name

fun innerSum(bound: Long): Long {
    var sum = 0L
    for (first in 0..99) {
        for (second in 0..99) {
            if (first + second < bound) sum += first + second
        }
    }
    return sum
}

fun collatz(input: Long): Long {
    if (input <= 0 || input >= 100) return 0L

    var value = input
    var steps = 0
    while (value != 1L) {
        value = if (value % 2 == 0L) value / 2 else value * 3 + 1
        steps++
    }
    return if (steps == 17) 1L else 2L
}

fun validTwoSum(values: List<Long>, target: Long, result: GoResult): Boolean {
    if (result.isPanic) return false

    val pair = result.list
    if (pair == null) {
        val seen = hashSetOf<Long>()
        for (value in values) {
            if (target - value in seen) return false
            seen += value
        }
        return true
    }

    if (pair.size != 2) return false
    val first = pair[0].longValue()
    val second = pair[1].longValue()
    if (first == second || first !in 0..<values.size.toLong() || second !in 0..<values.size.toLong()) return false
    return values[first.toInt()] + values[second.toInt()] == target
}

fun matchesNearbyDuplicate(values: List<Long>, distance: Long, result: GoResult): Boolean {
    val counts = hashMapOf<Long, Int>()
    for ((index, value) in values.withIndex()) {
        counts[value] = counts.getOrDefault(value, defaultValue = 0) + 1
        if (counts.getValue(value) >= 2) return result.isSuccess && result.value == true

        val previous = index.toLong() - distance
        if (previous >= 0) {
            if (previous >= values.size) return result.isPanic
            val previousValue = values[previous.toInt()]
            counts[previousValue] = counts.getOrDefault(previousValue, defaultValue = 0) - 1
        }
    }
    return result.isSuccess && result.value == false
}

fun matchesRooms(rooms: GoSlice, result: GoResult): Boolean {
    val keys = rooms.orEmpty().map { (it as? List<*>).longValues() }
    if (keys.size <= 1 || keys.any { it.isEmpty() || it.any { key -> key !in 0..<keys.size.toLong() } }) {
        return result.isPanic
    }

    val visited = hashSetOf<Int>()
    val pending = ArrayDeque<Int>()
    pending.addLast(0)
    while (pending.isNotEmpty()) {
        val room = pending.removeFirst()
        if (visited.add(room)) {
            keys[room].forEach { pending.addLast(it.toInt()) }
        }
    }
    return result.isSuccess && result.value == (visited.size == keys.size)
}
