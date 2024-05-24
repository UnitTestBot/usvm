package GoToJava

import java.io.BufferedReader
class main_someStruct {

	var fl1: String? = null
}

fun read_main_someStruct(buffReader: BufferedReader, id: Int): main_someStruct {
	val res = main_someStruct()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as main_someStruct
        }
        ptrMap[id] = res
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
    res.fl1 = mapDec[readType]?.invoke(buffReader, id) as String?

	buffReader.readLine()
	return res
}
