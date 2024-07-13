package org.usvm.annotations.ids

enum class ApproximationId(
    val pythonModule: String,
    val pythonName: String,
    var cRef: Long = 0L, // will be set during Python initialization
) {
    ListIndex("approximations.implementations.list", "IndexApproximation"),
    ListReverse("approximations.implementations.list", "ReverseApproximation"),
    ListConstructor("approximations.implementations.list", "ConstructorApproximation"),
    ListSort("approximations.implementations.list", "SortApproximation"),
    ListCopy("approximations.implementations.list", "CopyApproximation"),
    ListRemove("approximations.implementations.list", "RemoveApproximation"),
    ListCount("approximations.implementations.list", "CountApproximation"),
    DictConstructor("approximations.implementations.dict", "ConstructorApproximation"),
    DictGet("approximations.implementations.dict", "GetApproximation"),
    DictSetdefault("approximations.implementations.dict", "SetdefaultApproximation"),
    TupleIndex("approximations.implementations.tuple", "IndexApproximation"),
    TupleCount("approximations.implementations.tuple", "CountApproximation"),
    SetConstructor("approximations.implementations.set", "ConstructorApproximation"),
}
