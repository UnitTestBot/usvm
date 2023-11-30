package org.usvm.annotations.ids

enum class ApproximationId(
    val pythonModule: String,
    val pythonName: String,
    var cRef: Long = 0L  // will be set during Python initialization
) {
    ListIndex("approximations.implementations.list", "IndexApproximation"),
    ListReverse("approximations.implementations.list", "ReverseApproximation"),
    ListConstructor("approximations.implementations.list", "ConstructorApproximation"),
    SetConstructor("approximations.implementations.set", "ConstructorApproximation")
}