package org.usvm.annotations.ids

enum class SymbolicMethodId(
    var cName: String? = null, // will be set based on @CPythonAdapterJavaMethod
    var cRef: Long = 0L, // will be set in native code during Python initialization
) {
    Int,
    Float,
    Enumerate,
    ListAppend,
    ListInsert,
    ListPop,
    ListExtend,
    ListClear,
    SetAdd,
}
