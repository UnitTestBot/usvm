package com.spbpu.bbfinfrastructure.test

object ErrorCollector {

    val errorMap = mutableMapOf<String, Pair<String, String>>()
    val compilationErrors = mutableMapOf<String, Int>()

    fun putError(errorMessage: String) {
        if (ErrorCollector.compilationErrors.contains(errorMessage)) {
            ErrorCollector.compilationErrors[errorMessage] = ErrorCollector.compilationErrors[errorMessage]!! + 1
        } else {
            ErrorCollector.compilationErrors[errorMessage] = 1
        }
    }
}