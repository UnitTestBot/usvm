package org.usvm.annotations.ids

enum class NativeId(
    val pythonModule: String,
    val pythonName: String,
    var cRef: Long = 0L, // will be set during Python initialization
) {
    Eval("builtins", "eval"),
    OsSystem("os", "system"),
}
