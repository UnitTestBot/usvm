package org.usvm.annotations

enum class SymbolicMethodId(
    var cName: String? = null,  // will be set based on annotation CPythonAdapterJavaMethod
    var cRef: Long = 0L  // will be set in native code during Python initialization
) {
    Int,
    Float
}