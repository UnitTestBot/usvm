package com.spbpu.bbfinfrastructure.psicreator.idea

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.codeStyle.IndentHelper


class MockIndentHelper : IndentHelper() {
    override fun getIndent(p0: PsiFile, p1: ASTNode): Int = 0

    override fun getIndent(p0: PsiFile, p1: ASTNode, p2: Boolean): Int = 0

}
