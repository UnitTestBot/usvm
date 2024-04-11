package org.usvm.generated

import java.io.BufferedReader

class ast_Field {

	var Doc: ast_CommentGroup? = null
	var Names: List<ast_Ident>? = null
	var Type: Any? = null
	var Tag: ast_BasicLit? = null
	var Comment: ast_CommentGroup? = null
}

fun read_ast_Field(buffReader: BufferedReader, id: Int): ast_Field {
	val res = ast_Field()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_Field
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
    res.Doc = mapDec[readType]?.invoke(buffReader, id) as ast_CommentGroup?

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
    res.Names = mapDec[readType]?.invoke(buffReader, id) as List<ast_Ident>?

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
    res.Type = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.Tag = mapDec[readType]?.invoke(buffReader, id) as ast_BasicLit?

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
    res.Comment = mapDec[readType]?.invoke(buffReader, id) as ast_CommentGroup?

	buffReader.readLine()
	return res
}
