package com.spbpu.bbfinfrastructure.mutator.mutations.java.util

import com.intellij.psi.*
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyStatementList
import com.jetbrains.python.psi.impl.PyFunctionImpl
import com.jetbrains.python.psi.impl.PyNamedParameterImpl
import com.spbpu.bbfinfrastructure.util.getAllChildrenOfCurLevel
import com.spbpu.bbfinfrastructure.util.getAllChildrenWithItself
import org.jetbrains.kotlin.psi.psiUtil.parents

class PythonScopeCalculator: ScopeCalculator {

    override fun calcVariablesAndFunctionsFromScope(node: PsiElement): List<ScopeComponent> {
        val res = mutableSetOf<PythonScopeComponent>()
        for (parent in node.parents.toList()) {
            val currentLevelScope = when (parent) {
                is PyStatementList, is PyFile -> {
                    parent.getAllChildrenOfCurLevel().takeWhile { !it.getAllChildrenWithItself().contains(node) }
                        .filterIsInstance<PyAssignmentStatement>()
                        .mapNotNull { it.leftHandSideExpression?.name }
                        .map { PythonScopeComponent(it, "Any") }
                }

                is PyFunctionImpl -> {
                    parent.parameterList.children
                        .filter { it is PyNamedParameterImpl && it.name != "self"}
                        .map { it as PyNamedParameterImpl }
                        .mapNotNull { it.name }
                        .map { PythonScopeComponent(it, "Any") }
                }

//TODO! handle python classes
//                is PyClass -> {
//                    parent..map {
//                        val type = it.typeElement!!.text
////                        val mappedType = JavaTypeMappings.mappings[type] ?: type
////                        JavaScopeComponent(it.name, mappedType)
//                    }
//                }

                else -> emptyList()
            }
            res.addAll(currentLevelScope)
        }
        return res.toList()
    }


    class PythonScopeComponent(
        override val name: String, override val type: String
    ) : ScopeComponent(name, type) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PythonScopeComponent

            return name == other.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }

        override fun toString(): String = "PythonScopeComponent($name, ${type})"
    }
}