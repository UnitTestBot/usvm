package org.usvm.generated

import java.io.BufferedReader

class generatedInlineStruct_000 {

	var block: ssa_BasicBlock? = null
}

fun read_generatedInlineStruct_000(buffReader: BufferedReader, id: Int): generatedInlineStruct_000 {
	val res = generatedInlineStruct_000()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as generatedInlineStruct_000
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
    res.block = mapDec[readType]?.invoke(buffReader, id) as ssa_BasicBlock?

	buffReader.readLine()
	return res
}
