package org.usvm.machine.symbolicobjects

import org.usvm.USort
import org.usvm.machine.PyContext

sealed class PropertyOfPythonObject
class ContentOfType<out Sort : USort>(
    val id: String,
    val sort: (PyContext) -> Sort,
) : PropertyOfPythonObject()

object IntContents {
    val content = ContentOfType("int") { it.intSort }
}

object BoolContents {
    val content = ContentOfType("bool") { it.boolSort }
}

object FloatContents {
    const val BOUND = 300
    val content = ContentOfType("float") { it.realSort }
    val isNan = ContentOfType("is_nan_value") { it.intSort } // isNan <=> value > bound
    val infSign = ContentOfType("float_inf_sign") { it.boolSort }
    val isInf = ContentOfType("is_inf_value") { it.intSort } // isInf <=> value > bound
}

object ListIteratorContents {
    val list = ContentOfType("list_of_list_iterator") { it.addressSort }
    val index = ContentOfType("index_of_list_iterator") { it.intSort }
}

object RangeContents {
    val start = ContentOfType("start_of_range") { it.intSort }
    val stop = ContentOfType("stop_of_range") { it.intSort }
    val step = ContentOfType("step_of_range") { it.intSort }
    val length = ContentOfType("length_of_range") { it.intSort }
}

object RangeIteratorContents {
    val index = ContentOfType("index_of_range_iterator") { it.intSort }
    val start = ContentOfType("start_of_range_iterator") { it.intSort }
    val step = ContentOfType("step_of_range_iterator") { it.intSort }
    val length = ContentOfType("length_of_range_iterator") { it.intSort }
}

object TupleIteratorContents {
    val tuple = ContentOfType("tuple_of_tuple_iterator") { it.addressSort }
    val index = ContentOfType("index_of_tuple_iterator") { it.intSort }
}

object SliceContents {
    val start = ContentOfType("start_of_slice") { it.intSort }
    val startIsNone = ContentOfType("start_none_of_slice") { it.boolSort }
    val stop = ContentOfType("stop_of_slice") { it.intSort }
    val stopIsNone = ContentOfType("stop_none_of_slice") { it.boolSort }
    val step = ContentOfType("step_of_slice") { it.intSort }
    val stepIsNone = ContentOfType("step_none_of_slice") { it.boolSort }
}

object DictContents {
    val isNotEmpty = ContentOfType("dict_is_not_empty") { it.boolSort }
}

object SetContents {
    val isNotEmpty = ContentOfType("set_is_not_empty") { it.boolSort }
}

object EnumerateContents {
    val iterator = ContentOfType("iterator_of_enumerate") { it.addressSort }
    val index = ContentOfType("index_of_enumerate") { it.intSort }
}

object TimeOfCreation : PropertyOfPythonObject()
