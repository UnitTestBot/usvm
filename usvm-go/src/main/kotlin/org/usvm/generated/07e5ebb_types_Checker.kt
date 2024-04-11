package org.usvm.generated

import java.io.BufferedReader

class types_Checker {

	var conf: types_Config? = null
	var ctxt: types_Context? = null
	var fset: token_FileSet? = null
	var pkg: types_Package? = null
	var Info: types_Info? = null
	var version: types_version? = null
	var nextID: Long? = null
	var objMap: Map<Any, types_declInfo>? = null
	var impMap: Map<types_importKey, types_Package>? = null
	var valids: types_instanceLookup? = null
	var pkgPathMap: Map<String, Map<String, Boolean>>? = null
	var seenPkgMap: Map<types_Package, Boolean>? = null
	var files: List<ast_File>? = null
	var posVers: Map<token_File, types_version>? = null
	var imports: List<types_PkgName>? = null
	var dotImportMap: Map<types_dotImportKey, types_PkgName>? = null
	var recvTParamMap: Map<ast_Ident, types_TypeParam>? = null
	var brokenAliases: Map<types_TypeName, Boolean>? = null
	var unionTypeSets: Map<types_Union, types__TypeSet>? = null
	var mono: types_monoGraph? = null
	var firstErr: Any? = null
	var methods: Map<types_TypeName, List<types_Func>>? = null
	var untyped: Map<Any, types_exprInfo>? = null
	var delayed: List<types_action>? = null
	var objPath: List<Any>? = null
	var cleaners: List<Any>? = null
	var environment: types_environment? = null
	var indent: Long? = null
}

fun read_types_Checker(buffReader: BufferedReader, id: Int): types_Checker {
	val res = types_Checker()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_Checker
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
    res.conf = mapDec[readType]?.invoke(buffReader, id) as types_Config?

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
    res.ctxt = mapDec[readType]?.invoke(buffReader, id) as types_Context?

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
    res.fset = mapDec[readType]?.invoke(buffReader, id) as token_FileSet?

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
    res.pkg = mapDec[readType]?.invoke(buffReader, id) as types_Package?

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
    res.Info = mapDec[readType]?.invoke(buffReader, id) as types_Info?

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
    res.version = mapDec[readType]?.invoke(buffReader, id) as types_version?

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
    res.nextID = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.objMap = mapDec[readType]?.invoke(buffReader, id) as Map<Any, types_declInfo>?

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
    res.impMap = mapDec[readType]?.invoke(buffReader, id) as Map<types_importKey, types_Package>?

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
    res.valids = mapDec[readType]?.invoke(buffReader, id) as types_instanceLookup?

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
    res.pkgPathMap = mapDec[readType]?.invoke(buffReader, id) as Map<String, Map<String, Boolean>>?

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
    res.seenPkgMap = mapDec[readType]?.invoke(buffReader, id) as Map<types_Package, Boolean>?

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
    res.files = mapDec[readType]?.invoke(buffReader, id) as List<ast_File>?

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
    res.posVers = mapDec[readType]?.invoke(buffReader, id) as Map<token_File, types_version>?

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
    res.imports = mapDec[readType]?.invoke(buffReader, id) as List<types_PkgName>?

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
    res.dotImportMap = mapDec[readType]?.invoke(buffReader, id) as Map<types_dotImportKey, types_PkgName>?

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
    res.recvTParamMap = mapDec[readType]?.invoke(buffReader, id) as Map<ast_Ident, types_TypeParam>?

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
    res.brokenAliases = mapDec[readType]?.invoke(buffReader, id) as Map<types_TypeName, Boolean>?

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
    res.unionTypeSets = mapDec[readType]?.invoke(buffReader, id) as Map<types_Union, types__TypeSet>?

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
    res.mono = mapDec[readType]?.invoke(buffReader, id) as types_monoGraph?

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
    res.firstErr = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.methods = mapDec[readType]?.invoke(buffReader, id) as Map<types_TypeName, List<types_Func>>?

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
    res.untyped = mapDec[readType]?.invoke(buffReader, id) as Map<Any, types_exprInfo>?

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
    res.delayed = mapDec[readType]?.invoke(buffReader, id) as List<types_action>?

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
    res.objPath = mapDec[readType]?.invoke(buffReader, id) as List<Any>?

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
    res.cleaners = mapDec[readType]?.invoke(buffReader, id) as List<Any>?

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
    res.environment = mapDec[readType]?.invoke(buffReader, id) as types_environment?

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
    res.indent = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
