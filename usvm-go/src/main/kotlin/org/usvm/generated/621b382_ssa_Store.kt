package org.usvm.generated

import java.io.BufferedReader
import org.usvm.jacodb.*
import org.usvm.jacodb.GoInstLocationImpl
class ssa_Store : ssaToJacoInst {

	var anInstruction: generatedInlineStruct_000? = null
	var Addr: Any? = null
	var Val: Any? = null
	var pos: Long? = null

	override fun createJacoDBInst(parent: GoMethod): GoStoreInst {
        return GoStoreInst(
            GoInstLocationImpl(
                anInstruction!!.block!!.Index!!.toInt(),
                pos!!.toInt(),
                parent,
            ),
            parent,
            (Addr!! as ssaToJacoValue).createJacoDBValue(),
            (Val!! as ssaToJacoValue).createJacoDBValue()
        )
    }
}

fun read_ssa_Store(buffReader: BufferedReader, id: Int): ssa_Store {
	val res = ssa_Store()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as ssa_Store
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
    res.anInstruction = mapDec[readType]?.invoke(buffReader, id) as generatedInlineStruct_000?

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
    res.Addr = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.Val = mapDec[readType]?.invoke(buffReader, id) as Any?

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
    res.pos = mapDec[readType]?.invoke(buffReader, id) as Long?

	buffReader.readLine()
	return res
}
