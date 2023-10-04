package org.usvm.ps

import ai.onnxruntime.OnnxTensor
import kotlinx.coroutines.runBlocking
import org.usvm.constraints.UPathConstraints


fun <T> get2DShape(data: List<List<T>>): Pair<Int, Int> {
    if (data.isEmpty()) {
        return Pair(0, 0)
    }
    if (data[0].isEmpty()) {
        return Pair(data.size, 0)
    }
    return Pair(data.size, data[0].size)
}

fun <T> List<List<T>>.tensorNullIfEmpty(toTensor: (List<List<T>>) -> OnnxTensor): OnnxTensor {
    if (this.isEmpty()) {
        return toTensor(listOf(listOf(), listOf()))
    }

    return toTensor(this)
}

fun <T> List<List<T>>.transpose(): List<List<T>> {
    if (this.isEmpty()) {
        return listOf(listOf(), listOf())
    }

    val (rows, cols) = get2DShape(this)
    return List(cols) { j ->
        List(rows) { i ->
            this[i][j]
        }
    }
}

fun Boolean.toInt(): Int = if (this) 1 else 0

class IDGenerator {
    @Volatile
    var current: Int = 0

    fun issue(): Int {
        val result: Int
        runBlocking { result = current++ }
        return result
    }
}

fun <Type> UPathConstraints<Type, *>.size(): Int {
    return numericConstraints.constraints().count() +
            this.equalityConstraints.distinctReferences.count() +
            this.equalityConstraints.equalReferences.count() +
            this.equalityConstraints.referenceDisequalities.count() +
            this.equalityConstraints.nullableDisequalities.count() +
            this.logicalConstraints.count() +
            this.typeConstraints.symbolicRefToTypeRegion.count()  // TODO: maybe throw out?
}

