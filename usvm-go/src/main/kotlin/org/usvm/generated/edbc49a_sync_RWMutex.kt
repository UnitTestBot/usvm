package org.usvm.generated

import java.io.BufferedReader

class sync_RWMutex {

	var w: sync_Mutex? = null
	var writerSem: Long? = null
	var readerSem: Long? = null
	var readerCount: atomic_Int32? = null
	var readerWait: atomic_Int32? = null
}

fun read_sync_RWMutex(buffReader: BufferedReader, id: Int): sync_RWMutex {
	val res = sync_RWMutex()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as sync_RWMutex
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
    res.w = mapDec[readType]?.invoke(buffReader, id) as sync_Mutex?

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
    res.writerSem = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.readerSem = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.readerCount = mapDec[readType]?.invoke(buffReader, id) as atomic_Int32?

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
    res.readerWait = mapDec[readType]?.invoke(buffReader, id) as atomic_Int32?

	buffReader.readLine()
	return res
}
