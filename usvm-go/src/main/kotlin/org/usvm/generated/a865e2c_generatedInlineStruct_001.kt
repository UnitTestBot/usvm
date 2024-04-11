package org.usvm.generated

import java.io.BufferedReader

class generatedInlineStruct_001 {

	var value: types_MethodSet? = null
	var pointer: types_MethodSet? = null
}

fun read_generatedInlineStruct_001(buffReader: BufferedReader, id: Int): generatedInlineStruct_001 {
	val res = generatedInlineStruct_001()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as generatedInlineStruct_001
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
    res.value = mapDec[readType]?.invoke(buffReader, id) as types_MethodSet?

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
    res.pointer = mapDec[readType]?.invoke(buffReader, id) as types_MethodSet?

	buffReader.readLine()
	return res
}
