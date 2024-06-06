package com.spbpu.bbfinfrastructure.test

object ErrorCollector {

    val errorMap = mutableMapOf<String, Triple<String, String, String>>()
    val compilationErrors = mutableMapOf<String, Int>()

    fun putError(errorMessage: String) {
        if (compilationErrors.contains(errorMessage)) {
            compilationErrors[errorMessage] = compilationErrors[errorMessage]!! + 1
        } else {
            compilationErrors[errorMessage] = 1
        }
    }
}