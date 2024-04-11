package org.usvm.generated

import java.io.BufferedReader

class ast_TypeSpec {

	var Doc: ast_CommentGroup? = null
	var Name: ast_Ident? = null
	var TypeParams: ast_FieldList? = null
	var Assign: Long? = null
	var Type: Any? = null
	var Comment: ast_CommentGroup? = null
}

fun read_ast_TypeSpec(buffReader: BufferedReader, id: Int): ast_TypeSpec {
	val res = ast_TypeSpec()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_TypeSpec
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
    res.TypeParams = mapDec[readType]?.invoke(buffReader, id) as ast_FieldList?

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
    res.Assign = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.Comment = mapDec[readType]?.invoke(buffReader, id) as ast_CommentGroup?

	buffReader.readLine()
	return res
}
