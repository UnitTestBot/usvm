package com.spbpu.bbfinfrastructure.project

data class Header(
    val sourceFileName: String,
    val initialCWEs: List<Int>,
)  {

    companion object {
        fun createEmptyHeader(): Header = Header("", listOf())
    }
}
