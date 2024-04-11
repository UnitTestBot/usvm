package org.usvm.generated

import java.io.BufferedReader

class ast_ImportSpec {

	var Doc: ast_CommentGroup? = null
	var Name: ast_Ident? = null
	var Path: ast_BasicLit? = null
	var Comment: ast_CommentGroup? = null
	var EndPos: Long? = null
}

fun read_ast_ImportSpec(buffReader: BufferedReader, id: Int): ast_ImportSpec {
	val res = ast_ImportSpec()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_ImportSpec
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
    res.Name = mapDec[readType]?.invoke(buffReader, id) as ast_Ident?

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
    res.Path = mapDec[readType]?.invoke(buffReader, id) as ast_BasicLit?

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
    res.EndPos = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
