package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class atomic_Uint32 {

	var v: Long? = null
}

fun read_atomic_Uint32(buffReader: BufferedReader, id: Int): atomic_Uint32 {
	val res = atomic_Uint32()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as atomic_Uint32
        }
        ptrMap[id] = res
		structToPtrMap[res] = id
    }
    var line: String
    var split: List<String>
    var id: Int
    var readType: String

	line = buffReader.readLine()
	if (line == "end") {
        return res
    }
    split = line.split(" ")
    readType = split[1]
    id = -1
    if (split.size > 2) {
        id = split[2].toInt()
    }
    res.v = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
