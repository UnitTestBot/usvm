package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class ssa_Alloc : ssaToJacoExpr, ssaToJacoValue {

	var register: ssa_register? = null
	var Comment: String? = null
	var Heap: Boolean? = null
	var index: Long? = null

	override fun createJacoDBExpr(parent: GoMethod): GoAllocExpr {
        return GoAllocExpr(
            GoInstLocationImpl(
                register!!.anInstruction!!.block!!.Index!!.toInt(),
                register!!.pos!!.toInt(),
                parent
            ),
            (register!!.typ!! as ssaToJacoType).createJacoDBType(),
            "t${register!!.num!!.toInt()}"
        )
    }
	override fun createJacoDBValue(parent: GoMethod): GoValue {
		if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoAllocExpr
        }
        return createJacoDBExpr(parent)
    }

}

fun read_ssa_Alloc(buffReader: BufferedReader, id: Int): ssa_Alloc {
	val res = ssa_Alloc()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Alloc
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
    res.register = mapDec[readType]?.invoke(buffReader, id) as ssa_register?

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
    res.Comment = mapDec[readType]?.invoke(buffReader, id) as String?

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
    res.Heap = mapDec[readType]?.invoke(buffReader, id) as Boolean?

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
    res.index = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}