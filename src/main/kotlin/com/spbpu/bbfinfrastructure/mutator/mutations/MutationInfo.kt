package com.spbpu.bbfinfrastructure.mutator.mutations

class MutationInfo(
    val mutationName: String,
    val mutationDescription: String,
    val usedExtensions: List<String>,
    val location: MutationLocation
)

class MutationLocation(
    val fileName: String,
    val line: Int
)
