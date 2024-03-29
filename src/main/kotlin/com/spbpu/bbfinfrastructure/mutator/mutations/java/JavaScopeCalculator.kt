package com.spbpu.bbfinfrastructure.mutator.mutations.java

import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiMethodImpl
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.util.getAllChildrenOfCurLevel
import com.spbpu.bbfinfrastructure.util.getAllChildrenWithItself
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext

class JavaScopeCalculator(private val file: PsiFile, private val project: Project) {

    fun calcScope(node: PsiElement): List<JavaScopeComponent> {
        return  calcVariablesAndFunctionsFromScope(node)
    }


    private fun calcVariablesAndFunctionsFromScope(node: PsiElement): List<JavaScopeComponent> {
        val res = mutableSetOf<JavaScopeComponent>()
        for (parent in node.parents.toList()) {
            val currentLevelScope = when (parent) {
                is PsiCodeBlock -> {
                    parent.getAllChildrenOfCurLevel().takeWhile { !it.getAllChildrenWithItself().contains(node) }
                        .filterIsInstance<PsiDeclarationStatement>().flatMap { psiDeclaration ->
                            psiDeclaration.declaredElements.map {
                                val psiLocal = (it as? PsiLocalVariable)
                                val type = psiLocal?.type
                                if (psiLocal != null && type != null) {
                                    JavaScopeComponent(psiLocal.name, type)
                                } else {
                                    null
                                }
                            }
                        }
                }

                is PsiMethodImpl -> {
                    parent.parameters.map {
                        it.name?.let { name ->
                            JavaScopeComponent(name, it.type as PsiType)
                        }
                    }
                }

                is PsiClass -> {
                    parent.fields.map {
                        JavaScopeComponent(it.name, it.type)
                    }
                }

                is PsiForeachStatement -> {
                    listOf(
                        parent.iterationParameter.let {
                            JavaScopeComponent(it.name, it.type)
                        }
                    )
                }
                is PsiLambdaExpression -> {
                    parent.parameterList.parameters.map {
                        try {
                            JavaScopeComponent(it.name, it.type)
                        } catch (e: Throwable) {
                            null
                        }
                    }
                }
                else -> emptyList()
            }
            res.addAll(currentLevelScope.filterNotNull())
        }
        return res.toList()
    }


    class JavaScopeComponent(
        val name: String, val type: PsiType
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as JavaScopeComponent

            return name == other.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        override fun toString(): String = "JavaScopeComponent($name, ${type.presentableText})"
    }
}