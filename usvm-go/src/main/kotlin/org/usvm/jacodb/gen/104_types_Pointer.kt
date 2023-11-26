package org.usvm.jacodb.gen

import java.io.BufferedReader
import org.jacodb.go.api.*
class types_Pointer : ssaToJacoType {

	var base: Any? = null

	override fun createJacoDBType(): GoType {
        if (structToPtrMap.containsKey(this) && ptrToJacoMap.containsKey(structToPtrMap[this])) {
            return ptrToJacoMap[structToPtrMap[this]] as GoType
        }

        val res = PointerType(
            InterfaceType()
        )

        if (structToPtrMap.containsKey(this)) {
            ptrToJacoMap[structToPtrMap[this]!!] = res
        }
        res.baseType = (base!! as ssaToJacoType).createJacoDBType()

        return res
    }
}

fun read_types_Pointer(buffReader: BufferedReader, id: Int): types_Pointer {
	val res = types_Pointer()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_Pointer
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
    res.base = mapDec[readType]?.invoke(buffReader, id) as Any?

	buffReader.readLine()
	return res
}
