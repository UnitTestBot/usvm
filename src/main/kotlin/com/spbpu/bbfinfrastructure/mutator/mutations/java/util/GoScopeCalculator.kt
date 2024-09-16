package com.spbpu.bbfinfrastructure.mutator.mutations.java.util

import com.goide.psi.*
import com.goide.psi.impl.GoTypeSpecImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.spbpu.bbfinfrastructure.util.getAllChildrenOfCurLevel
import com.spbpu.bbfinfrastructure.util.getAllChildrenWithItself
import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

class GoScopeCalculator: ScopeCalculator {
    override fun calcVariablesAndFunctionsFromScope(node: PsiElement): List<ScopeComponent> {
        val res = mutableSetOf<GoScopeComponent>()
        for (parent in node.parents.toList()) {
            when (parent) {
                is GoBlock -> {
                    parent.getAllChildrenOfCurLevel().takeWhile { !it.getAllChildrenWithItself().contains(node) }
                        .filterIsInstance<GoSimpleStatement>()
                        .map {
                            it.shortVarDeclaration?.let { varDeclaration ->
                                val name = varDeclaration.varDefinitionList.first().name ?: return@map
                                val definedType =
                                    varDeclaration.rightExpressionsList.firstOrNull()?.let {
                                        val resolvedGoType = getGoType(it)
                                        try {
                                            val goSpecType =
                                                resolvedGoType?.resolve(ResolveState.initial()) as? GoTypeSpecImpl
                                            val struct =
                                                goSpecType?.getAllPSIChildrenOfType<GoStructType>()?.firstOrNull()
                                            struct?.fieldDeclarationList?.map {
                                                val fieldName = it.fieldDefinitionList.firstOrNull()?.name
                                                if (fieldName != null) {
                                                    res.add(
                                                        GoScopeComponent(
                                                            "$name.$fieldName",
                                                            it.type?.text ?: "any"
                                                        )
                                                    )
                                                }
                                            }
                                        } catch (e: Throwable) { }
                                        it.getAllChildrenOfCurLevel().firstOrNull { it is GoType }?.text ?: resolvedGoType?.text ?: "any"
                                    } ?: "any"
                                res.add(GoScopeComponent(name, definedType))
                            }
                        }
                }
                is GoFunctionOrMethodDeclaration -> {
                    if (parent is GoMethodDeclaration) {
                        parent.receiver?.let {
                            if (it.name != null) {
                                res.add(GoScopeComponent(it.name!!, it.type?.text ?: "any"))
                            }
                        }
                    }
                    parent.signature?.parameters?.parameterDeclarationList?.forEach {
                        val name = it.paramDefinitionList.first().name
                        if (name != null) {
                            res.add(GoScopeComponent(name, it.type?.text ?: "any"))
                        }
                    }
                }
                is GoForStatement -> {
                    parent.rangeClause!!.varDefinitionList.map {
                        val name = it.name
                        if (name != null && name != "_") {
                            res.add(GoScopeComponent(name, getGoType(it)?.text ?: "any"))
                        }
                    }

                }
            }
        }
        return res.toList()
    }


    private fun getGoType(goExpression: GoTypeOwner): GoType? =
        try {
            goExpression.getGoType(ResolveState.initial())
        } catch (e: Throwable) {
            null
        }

    class GoScopeComponent(
        override val name: String, override val type: String
    ): ScopeComponent(name, type) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as GoScopeComponent

            return name == other.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        override fun toString(): String = "GoScopeComponent($name, ${type})"
    }
}