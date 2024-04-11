package org.usvm.generated

import java.io.BufferedReader

fun readInteger(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine().toLong()
}

fun readString(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine().drop(1).dropLast(1)
}

fun readBoolean(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine() == "true"
}

fun readReal(buffReader: BufferedReader, id: Int): Any {
    return buffReader.readLine().toDouble()
}

fun readNil(buffReader: BufferedReader, id: Int): Any? {
    return null
}

fun readArray(buffReader: BufferedReader, id: Int): Any? {
    val res: MutableList<Any?> = mutableListOf()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id]
        }
        ptrMap[id] = res
    }
	var line = buffReader.readLine()
    while (line != "end") {
        var split: List<String>
        var id: Int
        split = line.split(" ")
        id = -1
        if (split.size > 1) {
            id = split[1].toInt()
        }
        res.add(mapDec[split[0]]?.invoke(buffReader, id))
        line = buffReader.readLine()
    }
    return res
}

fun readMap(buffReader: BufferedReader, id: Int): Any? {
    val res: MutableMap<Any?, Any?> = mutableMapOf()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id]
        }
        ptrMap[id] = res
    }
    var line = buffReader.readLine()
    while (line != "end") {
        var split: List<String>
        var id: Int
        split = line.split(" ")
        id = -1
        if (split.size > 1) {
            id = split[1].toInt()
        }
        val key = mapDec[split[0]]?.invoke(buffReader, id)
        line = buffReader.readLine()
        split = line.split(" ")
        id = -1
        if (split.size > 1) {
            id = split[1].toInt()
        }
        val value = mapDec[split[0]]?.invoke(buffReader, id)
        res[key] = value
        line = buffReader.readLine()
    }
    return res
}
