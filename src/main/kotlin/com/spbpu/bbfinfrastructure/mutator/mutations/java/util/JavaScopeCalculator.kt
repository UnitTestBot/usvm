package com.spbpu.bbfinfrastructure.mutator.mutations.java.util

import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiMethodImpl
import com.spbpu.bbfinfrastructure.util.JavaTypeMappings
import com.spbpu.bbfinfrastructure.util.getAllChildrenOfCurLevel
import com.spbpu.bbfinfrastructure.util.getAllChildrenWithItself
import org.jetbrains.kotlin.psi.psiUtil.parents

class JavaScopeCalculator: ScopeCalculator {

    override fun calcVariablesAndFunctionsFromScope(node: PsiElement): List<ScopeComponent> {
        val res = mutableSetOf<JavaScopeComponent>()
        for (parent in node.parents.toList()) {
            val currentLevelScope = when (parent) {
                is PsiCodeBlock -> {
                    parent.getAllChildrenOfCurLevel().takeWhile { !it.getAllChildrenWithItself().contains(node) }
                        .filterIsInstance<PsiDeclarationStatement>().flatMap { psiDeclaration ->
                            psiDeclaration.declaredElements.map {
                                val psiLocal = (it as? PsiLocalVariable)
                                val type = psiLocal?.typeElement?.text ?: psiLocal?.type?.presentableText
                                val mappedType = JavaTypeMappings.mappings[type] ?: type
                                if (psiLocal != null && mappedType != null) {
                                    JavaScopeComponent(psiLocal.name, mappedType)
                                } else {
                                    null
                                }
                            }
                        }
                }

                is PsiMethodImpl -> {
                    parent.parameterList.parameters.map {
                        if (it.typeElement?.text != null) {
                            val type = it.typeElement!!.text
                            val mappedType = JavaTypeMappings.mappings[type] ?: type
                            JavaScopeComponent(it.name, mappedType)
                        } else null
                    }
                }

                is PsiClass -> {
                    parent.fields.map {
                        val type = it.typeElement!!.text
                        val mappedType = JavaTypeMappings.mappings[type] ?: type
                        JavaScopeComponent(it.name, mappedType)
                    }
                }

                is PsiForeachStatement -> {
                    listOf(
                        parent.iterationParameter.let {
                            val type = it.typeElement!!.text
                            val mappedType = JavaTypeMappings.mappings[type] ?: type
                            JavaScopeComponent(it.name, mappedType)
                        }
                    )
                }
                is PsiLambdaExpression -> {
                    parent.parameterList.parameters.map {
                        try {
                            val type = it.typeElement!!.text
                            val mappedType = JavaTypeMappings.mappings[type] ?: type
                            JavaScopeComponent(it.name, mappedType)
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
        override val name: String, override val type: String
    ): ScopeComponent(name, type) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as JavaScopeComponent

            return name == other.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        override fun toString(): String = "JavaScopeComponent($name, ${type})"
    }
}