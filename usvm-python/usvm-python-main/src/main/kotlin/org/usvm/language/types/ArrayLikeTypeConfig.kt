package org.usvm.language.types

enum class ArrayLikeTypeConfig {
    Homogeneous,
    Heterogeneous
}

fun getTopLevelTypeStreamConfig(type: ArrayLikeConcretePythonType): ArrayLikeTypeConfig {
    return when (type.id) {
        type.owner.pythonList.id -> ArrayLikeTypeConfig.Homogeneous
        type.owner.pythonTuple.id -> ArrayLikeTypeConfig.Heterogeneous
        else -> error("Should not be reachable")
    }
}

fun getTopLevelTypeStreamVariant(type: ArrayLikeConcretePythonType): PythonType {
    val config = getTopLevelTypeStreamConfig(type)
    return when (config) {
        ArrayLikeTypeConfig.Homogeneous -> GenericType(type.original ?: type)
        ArrayLikeTypeConfig.Heterogeneous -> type.original ?: type
    }
}