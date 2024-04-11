package org.usvm.generated

import java.io.BufferedReader

class ast_CommentGroup {

	var List: List<ast_Comment>? = null
}

fun read_ast_CommentGroup(buffReader: BufferedReader, id: Int): ast_CommentGroup {
	val res = ast_CommentGroup()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_CommentGroup
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
    res.List = mapDec[readType]?.invoke(buffReader, id) as List<ast_Comment>?

	buffReader.readLine()
	return res
}
