package org.usvm.generated

import java.io.BufferedReader

class sync_Once {

	var done: Long? = null
	var m: sync_Mutex? = null
}

fun read_sync_Once(buffReader: BufferedReader, id: Int): sync_Once {
	val res = sync_Once()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as sync_Once
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
    res.done = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.m = mapDec[readType]?.invoke(buffReader, id) as sync_Mutex?

	buffReader.readLine()
	return res
}
