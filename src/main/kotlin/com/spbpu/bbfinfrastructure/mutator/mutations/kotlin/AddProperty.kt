package com.spbpu.bbfinfrastructure.mutator.mutations.kotlin

import com.intellij.psi.PsiWhiteSpace
import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType
import com.spbpu.bbfinfrastructure.util.getRandomPlaceToInsertNewLine

class AddProperty: Transformation() {
    override fun transform() {
    }

    fun addProperty() {
        val randomPlaceToInsertIn = file.getRandomPlaceToInsertNewLine()
//        val randomPropertyFromAnotherTree =

    }
}