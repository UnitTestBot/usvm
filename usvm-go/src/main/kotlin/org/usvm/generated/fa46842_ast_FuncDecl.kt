package org.usvm.generated

import java.io.BufferedReader

class ast_FuncDecl {

	var Doc: ast_CommentGroup? = null
	var Recv: ast_FieldList? = null
	var Name: ast_Ident? = null
	var Type: ast_FuncType? = null
	var Body: ast_BlockStmt? = null
}

fun read_ast_FuncDecl(buffReader: BufferedReader, id: Int): ast_FuncDecl {
	val res = ast_FuncDecl()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ast_FuncDecl
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
    res.Recv = mapDec[readType]?.invoke(buffReader, id) as ast_FieldList?

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
    res.Type = mapDec[readType]?.invoke(buffReader, id) as ast_FuncType?

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
    res.Body = mapDec[readType]?.invoke(buffReader, id) as ast_BlockStmt?

	buffReader.readLine()
	return res
}
