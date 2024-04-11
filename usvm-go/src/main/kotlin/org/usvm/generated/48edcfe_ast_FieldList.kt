package org.usvm.generated

import java.io.BufferedReader

class ast_FieldList {

	var Opening: Long? = null
	var List: List<ast_Field>? = null
	var Closing: Long? = null
}

fun read_ast_FieldList(buffReader: BufferedReader, id: Int): ast_FieldList {
	val res = ast_FieldList()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_FieldList
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
    res.Opening = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.List = mapDec[readType]?.invoke(buffReader, id) as List<ast_Field>?

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
    res.Closing = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
