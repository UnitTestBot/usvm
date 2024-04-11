package org.usvm.generated

import java.io.BufferedReader

class ssa_domInfo {

	var idom: ssa_BasicBlock? = null
	var children: List<ssa_BasicBlock>? = null
	var pre: Long? = null
	var post: Long? = null
}

fun read_ssa_domInfo(buffReader: BufferedReader, id: Int): ssa_domInfo {
	val res = ssa_domInfo()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_domInfo
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
    res.idom = mapDec[readType]?.invoke(buffReader, id) as ssa_BasicBlock?

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
    res.children = mapDec[readType]?.invoke(buffReader, id) as List<ssa_BasicBlock>?

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
    res.pre = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.post = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
