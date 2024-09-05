package com.spbpu.bbfinfrastructure.mutator.mutations.java.util

import com.intellij.psi.PsiElement

interface ScopeCalculator {
    fun calcVariablesAndFunctionsFromScope(node: PsiElement): List<ScopeComponent>
}

abstract class ScopeComponent(
    open val name: String,
    open val type: String
)