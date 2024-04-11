package org.usvm.generated

import java.io.BufferedReader

class ast_Comment {

	var Slash: Long? = null
	var Text: String? = null
}

fun read_ast_Comment(buffReader: BufferedReader, id: Int): ast_Comment {
	val res = ast_Comment()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_Comment
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
    res.Slash = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.Text = mapDec[readType]?.invoke(buffReader, id) as String?

	buffReader.readLine()
	return res
}
