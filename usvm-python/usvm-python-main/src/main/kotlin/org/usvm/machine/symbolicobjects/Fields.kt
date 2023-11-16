package org.usvm.machine.symbolicobjects

sealed class PropertyOfPythonObject
data class ContentOfType(val id: String): PropertyOfPythonObject()

object IntContents {
    val content = ContentOfType("int")
}

object BoolContents {
    val content = ContentOfType("bool")
}

object FloatContents {
    const val bound = 300
    val content = ContentOfType("float")
    val isNan = ContentOfType("is_nan_value")  // int field; isNan <=> value > bound
    val infSign = ContentOfType("float_inf_sign")
    val isInf = ContentOfType("is_inf_value")  // int field; isInf <=> value > bound
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

object SliceContents {
    val start = ContentOfType("start_of_slice")
    val startIsNone = ContentOfType("start_none_of_slice")
    val stop = ContentOfType("stop_of_slice")
    val stopIsNone = ContentOfType("stop_none_of_slice")
    val step = ContentOfType("step_of_slice")
    val stepIsNone = ContentOfType("step_none_of_slice")
}

object TimeOfCreation: PropertyOfPythonObject()