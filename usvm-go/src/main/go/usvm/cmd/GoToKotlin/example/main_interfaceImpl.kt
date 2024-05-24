package GoToJava

import java.io.BufferedReader
class main_interfaceImpl {

	var fl21: List<String>? = null
}

fun read_main_interfaceImpl(buffReader: BufferedReader, id: Int): main_interfaceImpl {
	val res = main_interfaceImpl()
    if (id != -1) {
        if (ptrMap.containsKey(id)) {
            return ptrMap[id] as main_interfaceImpl
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
    res.fl21 = mapDec[readType]?.invoke(buffReader, id) as List<String>?

	buffReader.readLine()
	return res
}
