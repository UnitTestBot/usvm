package com.spbpu.bbfinfrastructure.util

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import java.io.File

class NodeCollector(val dir: String) {
    val database = mutableMapOf<IElementType, MutableSet<String>>()

    companion object {
        val excludes = TokenSet.create(
            KtTokens.PACKAGE_KEYWORD,
            KtTokens.AS_KEYWORD,
            KtTokens.TYPE_ALIAS_KEYWORD,
            KtTokens.CLASS_KEYWORD,
            KtTokens.INTERFACE_KEYWORD,
            KtTokens.THIS_KEYWORD,
            KtTokens.SUPER_KEYWORD,
            KtTokens.VAL_KEYWORD,
            KtTokens.VAR_KEYWORD,
            KtTokens.FUN_KEYWORD,
            KtTokens.FOR_KEYWORD,
            KtTokens.NULL_KEYWORD,
            KtTokens.TRUE_KEYWORD,
            KtTokens.FALSE_KEYWORD,
            KtTokens.IS_KEYWORD,
            KtTokens.IN_KEYWORD,
            KtTokens.THROW_KEYWORD,
            KtTokens.RETURN_KEYWORD,
            KtTokens.BREAK_KEYWORD,
            KtTokens.CONTINUE_KEYWORD,
            KtTokens.OBJECT_KEYWORD,
            KtTokens.IF_KEYWORD,
            KtTokens.ELSE_KEYWORD,
            KtTokens.WHILE_KEYWORD,
            KtTokens.DO_KEYWORD,
            KtTokens.TRY_KEYWORD,
            KtTokens.WHEN_KEYWORD,
            KtTokens.NOT_IN,
            KtTokens.NOT_IS,
            KtTokens.AS_SAFE,
            KtTokens.TYPEOF_KEYWORD,
            KtTokens.FILE_KEYWORD,
            KtTokens.IMPORT_KEYWORD,
            KtTokens.WHERE_KEYWORD,
            KtTokens.BY_KEYWORD,
            KtTokens.GET_KEYWORD,
            KtTokens.SET_KEYWORD,
            KtTokens.ABSTRACT_KEYWORD,
            KtTokens.ENUM_KEYWORD,
            KtTokens.OPEN_KEYWORD,
            KtTokens.INNER_KEYWORD,
            KtTokens.OVERRIDE_KEYWORD,
            KtTokens.PRIVATE_KEYWORD,
            KtTokens.PUBLIC_KEYWORD,
            KtTokens.INTERNAL_KEYWORD,
            KtTokens.PROTECTED_KEYWORD,
            KtTokens.CATCH_KEYWORD,
            KtTokens.FINALLY_KEYWORD,
            KtTokens.OUT_KEYWORD,
            KtTokens.FINAL_KEYWORD,
            KtTokens.VARARG_KEYWORD,
            KtTokens.REIFIED_KEYWORD,
            KtTokens.DYNAMIC_KEYWORD,
            KtTokens.COMPANION_KEYWORD,
            KtTokens.CONSTRUCTOR_KEYWORD,
            KtTokens.INIT_KEYWORD,
            KtTokens.SEALED_KEYWORD,
            KtTokens.FIELD_KEYWORD,
            KtTokens.PROPERTY_KEYWORD,
            KtTokens.RECEIVER_KEYWORD,
            KtTokens.PARAM_KEYWORD,
            KtTokens.SETPARAM_KEYWORD,
            KtTokens.DELEGATE_KEYWORD,
            KtTokens.LATEINIT_KEYWORD,
            KtTokens.DATA_KEYWORD,
            KtTokens.INLINE_KEYWORD,
            KtTokens.NOINLINE_KEYWORD,
            KtTokens.TAILREC_KEYWORD,
            KtTokens.EXTERNAL_KEYWORD,
            KtTokens.ANNOTATION_KEYWORD,
            KtTokens.CROSSINLINE_KEYWORD,
            KtTokens.CONST_KEYWORD,
            KtTokens.OPERATOR_KEYWORD,
            KtTokens.INFIX_KEYWORD,
            KtTokens.SUSPEND_KEYWORD,
            KtTokens.HEADER_KEYWORD,
            KtTokens.IMPL_KEYWORD,
            KtTokens.EXPECT_KEYWORD,
            KtTokens.ACTUAL_KEYWORD,
            KtTokens.AS_KEYWORD,
            KtTokens.AS_SAFE,
            KtTokens.IS_KEYWORD,
            KtTokens.IN_KEYWORD,
            KtTokens.DOT,
            KtTokens.PLUSPLUS,
            KtTokens.MINUSMINUS,
            KtTokens.EXCLEXCL,
            KtTokens.MUL,
            KtTokens.PLUS,
            KtTokens.MINUS,
            KtTokens.EXCL,
            KtTokens.DIV,
            KtTokens.PERC,
            KtTokens.LT,
            KtTokens.GT,
            KtTokens.LTEQ,
            KtTokens.GTEQ,
            KtTokens.EQEQEQ,
            KtTokens.EXCLEQEQEQ,
            KtTokens.EQEQ,
            KtTokens.EXCLEQ,
            KtTokens.ANDAND,
            KtTokens.OROR,
            KtTokens.SAFE_ACCESS,
            KtTokens.ELVIS,
            KtTokens.RANGE,
            KtTokens.EQ,
            KtTokens.MULTEQ,
            KtTokens.DIVEQ,
            KtTokens.PERCEQ,
            KtTokens.PLUSEQ,
            KtTokens.MINUSEQ,
            KtTokens.NOT_IN,
            KtTokens.NOT_IS,
            KtTokens.PLUSEQ,
            KtTokens.MINUSEQ,
            KtTokens.MULTEQ,
            KtTokens.PERCEQ,
            KtTokens.DIVEQ,
            KtTokens.WHITE_SPACE,
            KtTokens.LPAR,
            KtTokens.RPAR
        )
    }


    fun collect() {
        val size = File(dir).listFiles().filter { it.name.endsWith(".kt") }.size
        for ((ind, f) in File(dir).listFiles().filter { it.name.endsWith(".kt") }.withIndex()) {
            println("HANDLING $ind from $size file")
            val psiFile = PSICreator.getPSIForFile(f.path)
            for (node in psiFile.node.getAllChildrenNodes()) {
                if (!excludes.contains(node.elementType))
                    database.getOrPut(node.elementType) { mutableSetOf(f.name) }.add(f.name)
            }
        }
        if (File("database.txt").exists()) File("database.txt").delete()
        val res = File("database.txt")
        database.forEach {
            res.appendText("${it.key} ${it.value}\n")
        }
    }

    fun collectJavaDB() {
        val size = File(dir).listFiles().filter { it.name.endsWith(".java") }.size
        val proj = PSICreator.getPSIForText("").project
        for ((ind, f) in File(dir).listFiles().filter { it.name.endsWith(".java") }.withIndex()) {
            println("HANDLING $ind from $size file")
            val psiFile = PSICreator.getPsiForJava(f.readText())
            for (node in psiFile.node.getAllChildrenNodes()) {
                database.getOrPut(node.elementType) { mutableSetOf(f.name) }.add(f.name)
            }
        }
        if (File("databaseJava.txt").exists()) File("databaseJava.txt").delete()
        val res = File("databaseJava.txt")
        database.forEach {
            res.appendText("${it.key} ${it.value}\n")
        }
    }
}