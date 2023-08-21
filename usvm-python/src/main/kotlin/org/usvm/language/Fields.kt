package org.usvm.language

sealed class PropertyOfPythonObject
data class ContentOfType(val id: String): PropertyOfPythonObject()

object IntContents {
    val content = ContentOfType("int")
}
object BoolContents {
    val content = ContentOfType("bool")
}
object ListIteratorContents {
    val list = ContentOfType("list_of_list_iterator")
    val index = ContentOfType("index_of_list_iterator")
}

object RangeContents {
    val start = ContentOfType("start_of_range")
    val stop = ContentOfType("stop_of_range")
    val step = ContentOfType("step_of_range")
    val length = ContentOfType("length_of_range")
}

object RangeIteratorContents {
    val index = ContentOfType("index_of_range_iterator")
    val start = ContentOfType("start_of_range_iterator")
    val step = ContentOfType("step_of_range_iterator")
    val length = ContentOfType("length_of_range_iterator")
}

object TupleIteratorContents {
    val tuple = ContentOfType("tuple_of_tuple_iterator")
    val index = ContentOfType("index_of_tuple_iterator")
}