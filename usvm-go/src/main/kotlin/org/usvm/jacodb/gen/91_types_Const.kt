package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class types_Const {

	var Object: types_object? = null
	var Val: Any? = null
}

fun read_types_Const(buffReader: BufferedReader, id: Int): types_Const {
	val res = types_Const()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_Const
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
    res.Object = mapDec[readType]?.invoke(buffReader, id) as types_object?

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
    res.Val = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}