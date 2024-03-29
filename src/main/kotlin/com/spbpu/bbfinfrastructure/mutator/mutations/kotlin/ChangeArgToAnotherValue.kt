package com.spbpu.bbfinfrastructure.mutator.mutations.kotlin

import com.intellij.psi.PsiElement
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.util.findPsi
import com.spbpu.bbfinfrastructure.util.getAllPSIChildrenOfType
import com.spbpu.bbfinfrastructure.util.getAvailableValuesToInsertIn
import com.spbpu.bbfinfrastructure.util.getTrue
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ExpressionValueArgument
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import kotlin.random.Random

class ChangeArgToAnotherValue : Transformation() {


    override fun transform() {
        val ctx = PSICreator.analyze(file, project) ?: return
        for (func in file.getAllPSIChildrenOfType<KtNamedFunction>()) {
            val callers = file.getAllPSIChildrenOfType<KtCallExpression>()
                .filter { it.getResolvedCall(ctx)?.resultingDescriptor?.findPsi() == func }
            for (call in callers.filter { Random.getTrue(30) }) {
                val valueArgs = call.getResolvedCall(ctx)?.valueArguments?.entries ?: continue
                for ((i, arg) in valueArgs.withIndex().filter { Random.getTrue(30) }) {
                    val argType = arg.key.type ?: continue
                    val argPSI =
                        (arg.value as? ExpressionValueArgument)?.valueArgument?.getArgumentExpression() ?: continue
                    val replacement =
                        if (Random.getTrue(60)) {
                            getAvailablePropsAndExpressionsOfCompTypes(argPSI, argType, ctx)
                        } else {
                            null
                        } ?: return
                    checker.replaceNodeIfPossible(argPSI, replacement)
                }
            }
        }
    }


    private fun getAvailablePropsAndExpressionsOfCompTypes(
        node: PsiElement,
        type: KotlinType,
        ctx: BindingContext
    ): KtExpression? {
        val potentialReplacement =
            (file as KtFile).getAvailableValuesToInsertIn(node, ctx)
                .filter { it.second != null }
                .map { it.first to it.second!! }
                .randomOrNull() ?: return null
        return if (potentialReplacement.second == type) {
            potentialReplacement.first
        } else {
            null
        }
    }
}