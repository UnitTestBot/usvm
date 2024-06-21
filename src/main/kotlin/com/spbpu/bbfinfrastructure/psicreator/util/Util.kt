package com.spbpu.bbfinfrastructure.psicreator.util

import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiWhiteSpace
import com.spbpu.bbfinfrastructure.util.getAllChildren

/**
 * Created by akhin on 11/7/16.
 */

inline fun <R> function(body: () -> R) = body()

fun PsiElementFactory.createEmptyWhitespace() = createCodeBlockFromText("{}", null).getAllChildren().find { it is PsiWhiteSpace && it.text == "" }

fun PsiElementFactory.createWhitespace() = createCodeBlockFromText("{\n}", null).children[1]
