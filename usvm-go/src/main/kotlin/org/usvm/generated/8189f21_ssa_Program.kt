package org.usvm.generated

import java.io.BufferedReader
import org.usvm.jacodb.*
class ssa_Program {

	var Fset: token_FileSet? = null
	var imported: Map<String, ssa_Package>? = null
	var packages: Map<types_Package, ssa_Package>? = null
	var mode: Long? = null
	var MethodSets: typeutil_MethodSetCache? = null
	var canon: ssa_canonizer? = null
	var ctxt: types_Context? = null
	var methodsMu: sync_Mutex? = null
	var methodSets: typeutil_Map? = null
	var parameterized: ssa_tpWalker? = null
	var runtimeTypesMu: sync_Mutex? = null
	var runtimeTypes: typeutil_Map? = null
	var objectMethodsMu: sync_Mutex? = null
	var objectMethods: Map<types_Func, ssa_Function>? = null

	fun createJacoDBProject(): GoProject {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoProject
        }


        val methods = mutableListOf<GoMethod>()
        for (pkg in packages!!) {
            for (member in pkg.value.Members!!) {
                if (member.value is ssa_Function) {
                    methods.add((member.value as ssa_Function).createJacoDBMethod())
                }
            }
        }

        val res = GoProject(
            methods.toList()
        )
		if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        return res
    }
}

fun read_ssa_Program(buffReader: BufferedReader, id: Int): ssa_Program {
	val res = ssa_Program()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Program
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
    res.Fset = mapDec[readType]?.invoke(buffReader, id) as token_FileSet?

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
    res.imported = mapDec[readType]?.invoke(buffReader, id) as Map<String, ssa_Package>?

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
    res.packages = mapDec[readType]?.invoke(buffReader, id) as Map<types_Package, ssa_Package>?

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
    res.mode = mapDec[readType]?.invoke(buffReader, id) as Long?

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
    res.MethodSets = mapDec[readType]?.invoke(buffReader, id) as typeutil_MethodSetCache?

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
    res.canon = mapDec[readType]?.invoke(buffReader, id) as ssa_canonizer?

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
    res.methodsMu = mapDec[readType]?.invoke(buffReader, id) as sync_Mutex?

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
    res.methodSets = mapDec[readType]?.invoke(buffReader, id) as typeutil_Map?

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
    res.parameterized = mapDec[readType]?.invoke(buffReader, id) as ssa_tpWalker?

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
    res.runtimeTypesMu = mapDec[readType]?.invoke(buffReader, id) as sync_Mutex?

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
    res.runtimeTypes = mapDec[readType]?.invoke(buffReader, id) as typeutil_Map?

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
    res.objectMethodsMu = mapDec[readType]?.invoke(buffReader, id) as sync_Mutex?

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
    res.objectMethods = mapDec[readType]?.invoke(buffReader, id) as Map<types_Func, ssa_Function>?

	buffReader.readLine()
	return res
}
