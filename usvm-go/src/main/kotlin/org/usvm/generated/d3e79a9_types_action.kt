package org.usvm.generated

import java.io.BufferedReader

class types_action {

	var desc: types_actionDesc? = null
}

fun read_types_action(buffReader: BufferedReader, id: Int): types_action {
	val res = types_action()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_action
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
    res.desc = mapDec[readType]?.invoke(buffReader, id) as types_actionDesc?

	buffReader.readLine()
	return res
}
