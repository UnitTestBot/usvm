package org.usvm.generated

import java.io.BufferedReader
import org.usvm.jacodb.GoType
class types_Union : GoType {

	var terms: List<types_Term>? = null

	override val typeName: String
        get(): String {
            var res = "enum {\n"
            for (t in terms!!) {
                res += (t.typ!! as GoType).typeName + ",\n"
            }
            return "$res}"
        }
}

fun read_types_Union(buffReader: BufferedReader, id: Int): types_Union {
	val res = types_Union()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as types_Union
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
    res.terms = mapDec[readType]?.invoke(buffReader, id) as List<types_Term>?

	buffReader.readLine()
	return res
}
