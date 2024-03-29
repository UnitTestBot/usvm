package com.spbpu.bbfinfrastructure.util

import com.intellij.psi.PsiElement
import com.spbpu.bbfinfrastructure.project.Project
import com.spbpu.bbfinfrastructure.psicreator.PSICreator
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.psicreator.util.Factory.tryToCreateExpression
import org.jetbrains.kotlin.cfg.getDeclarationDescriptorIncludingConstructors
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.synthetics.findClassDescriptor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import java.lang.StringBuilder
import kotlin.collections.flatMap
import kotlin.random.Random

class ScopeCalculator(private val file: KtFile, private val project: Project) {

    var ctx: BindingContext? = null

    fun calcScope(node: PsiElement): List<ScopeComponent> {
        ctx = PSICreator.analyze(file, project) ?: return emptyList()
        val res = calcVariablesAndFunctionsFromScope(node)
        //res.map { it.psiElement.text + " ${it.type} \n__________________________\n" }.forEach(::println)
        return res
    }

    companion object {

        fun processScope(
//            rig: RandomInstancesGenerator,
            scope: List<ScopeComponent>,
            generatedFunCalls: MutableMap<FunctionDescriptor, KtExpression?>
        ): List<ScopeComponent> {
            val processedScope = mutableListOf<ScopeComponent>()
            for (scopeEl in scope) {
                val expressionToCall =
                    when (scopeEl.declaration) {
                        is PropertyDescriptor -> {
                            scopeEl.declaration.name.asString()
                        }
                        is ParameterDescriptor -> {
                            scopeEl.declaration.name.asString()
                        }
                        is VariableDescriptor -> {
                            scopeEl.declaration.name.asString()
                        }
                        is FunctionDescriptor -> {
                            val serialized = generatedFunCalls[scopeEl.declaration]
                            if (serialized == null) {
                                val generatedCall = generateCallExpr(/*rig, */scopeEl.declaration, processedScope)
                                generatedFunCalls[scopeEl.declaration] = generatedCall
                                generatedCall?.text ?: ""
                            } else {
                                serialized.text ?: ""
                            }
                        }
                        else -> {
                            scopeEl.psiElement.text
                        }
                    }.let { Factory.psiFactory.tryToCreateExpression(it) }
                if (expressionToCall != null && expressionToCall.text.isNotEmpty()) {
                    processedScope.add(ScopeComponent(expressionToCall, scopeEl.declaration, scopeEl.type))
                }
            }
            return processedScope
        }

        //We are not expecting typeParams
        fun generateCallExpr(
//            rig: RandomInstancesGenerator,
            func: CallableDescriptor,
            scopeElements: List<ScopeCalculator.ScopeComponent>
        ): KtExpression? {
//            Transformation.log.debug("GENERATION of call of type $func")
            val name = func.name
            val valueParams = func.valueParameters.map { vp ->
                val fromUsages = scopeElements.filter { usage ->
                    vp.type.getNameWithoutError().trim() == usage.type.getNameWithoutError().trim()
                }
                if (fromUsages.isNotEmpty()) fromUsages.random().psiElement.text
                else return null
                //getInsertableExpressions(Pair(it, it.typeReference?.getAbbreviatedTypeOrType()), 1).randomOrNull()
            }
            if (valueParams.any { it.isEmpty() }) {
//                Transformation.log.debug("CANT GENERATE PARAMS FOR $func")
                return null
            }
            val inv = "$name(${valueParams.joinToString()})"
            return Factory.psiFactory.tryToCreateExpression(inv)
        }
    }


    private fun calcVariablesAndFunctionsFromScope(node: PsiElement): List<ScopeComponent> {
        val res = mutableSetOf<ScopeComponent>()
        for (parent in node.parents.toList()) {
            val currentLevelScope =
                when (parent) {
                    is KtBlockExpression -> {
                        parent.psi.getAllChildrenOfCurLevel()
                            .takeWhile { !it.getAllChildrenWithItself().contains(node) }
                            .filter { it is KtNamedFunction || it is KtProperty || it is KtCallExpression || it is KtParameter }
                            .mapNotNull { getDeclarationAndTypeForScopeComp(it) }
                    }
                    is KtNamedFunction -> {
                        parent.valueParameters
                            .takeWhile { !it.getAllChildrenWithItself().contains(node) }
                            .mapNotNull { getDeclarationAndTypeForScopeComp(it) }
                    }
                    is KtClassOrObject -> {
                        getParentClassScope(parent, node)
                    }
                    is KtForExpression -> {
                        val t = parent.getLoopParameterType(ctx!!)
                        val psiRange = parent.loopParameter
                        if (t != null && psiRange != null) {
                            listOf(ScopeComponent(psiRange, null, t))
                        } else {
                            emptyList()
                        }
                    }
                    is KtLambdaExpression -> {
                        val typesOfValueParams = parent.getType(ctx!!)?.arguments?.getAllWithoutLast() ?: emptyList()
                        val valueParams =
                            when {
                                typesOfValueParams.isEmpty() -> listOf()
                                parent.valueParameters.isNotEmpty() -> parent.valueParameters
                                else -> listOf(Factory.psiFactory.createExpression("it"))
                            }
                        valueParams
                            .takeWhile { !it.getAllChildrenWithItself().contains(node) }
                            .zip(typesOfValueParams)
                            .map { ScopeComponent(it.first, null, it.second.type) }
                    }
                    is KtWhenExpression -> {
                        parent.subjectVariable?.let { prop ->
                            val declAndType = getDeclarationAndTypeForScopeComp(prop)
                            declAndType?.let { listOf(it) } ?: emptyList()
                        } ?: emptyList()
                    }
                    is KtFile -> {
                        project.files
                            .flatMap { it.psiFile.getAllPSIChildrenOfTwoTypes<KtNamedFunction, KtProperty>() }
                            .filter { it.isTopLevelKtOrJavaMember() }
                            .mapNotNull { getDeclarationAndTypeForScopeComp(it) }
                    }
                    else -> emptyList()
                }
            val filteredByAlreadyContainedNames =
                currentLevelScope.filter { res.all { elFromResScope -> elFromResScope.psiElement.text != it.psiElement.text } }
            res.addAll(filteredByAlreadyContainedNames)
        }
        return res.filter { filterNonInterestingDeclarations(it, node) }.toList()
    }

    private fun getDeclarationAndTypeForScopeComp(psi: PsiElement): ScopeComponent? {
        val res = getDeclarationForScopeComp(psi) to getTypeForScopeComp(psi)
        return if (res.second == null) {
            null
        } else {
            ScopeComponent(psi, res.first, res.second!!)
        }
    }

    private fun getDeclarationForScopeComp(element: PsiElement) =
        when (element) {
            is KtProperty -> element.getDeclarationDescriptorIncludingConstructors(ctx!!)
            is KtNamedFunction -> element.getDeclarationDescriptorIncludingConstructors(ctx!!)
            is KtParameter -> element.getDeclarationDescriptorIncludingConstructors(ctx!!)
            else -> null
        }

    private fun getTypeForScopeComp(element: PsiElement) =
        when (element) {
            is KtProperty -> element.getPropertyType(ctx!!)
            is KtNamedFunction -> element.getReturnType(ctx!!)
            is KtParameter -> element.typeReference?.getAbbreviatedTypeOrType(ctx!!)
            is KtCallExpression -> element.getType(ctx!!)
            else -> null
        }

    private fun getParentClassScope(klass: KtClassOrObject, node: PsiElement): List<ScopeComponent> {
        val klassDescriptor = klass.getDeclarationDescriptorIncludingConstructors(ctx!!) as? ClassDescriptor
        val klassDeclarations =
            klassDescriptor?.unsubstitutedMemberScope
                ?.getDescriptorsFiltered { true }
                ?.mapNotNull { it.findPsi() } ?: emptyList()
        val constructorProperties =
            klass.primaryConstructor?.valueParameters?.filter {
                if (isClassPropertyInitializer(klass, node)) true else it.isPropertyParameter()
            } ?: emptyList()
        return (klassDeclarations + constructorProperties).mapNotNull { getDeclarationAndTypeForScopeComp(it) }
    }

    private fun isClassPropertyInitializer(klass: KtClassOrObject, node: PsiElement) =
        klass.body?.properties?.any {
            it.initializer == node || it.initializer?.getAllChildren()?.any { it == node } == true
        } ?: false

    private fun filterNonInterestingDeclarations(scopeComponent: ScopeComponent, node: PsiElement): Boolean {
        val psiElement = scopeComponent.psiElement
        if (psiElement is KtNamedFunction && psiElement.name?.contains("box") == true) return false
        if (psiElement is KtNamedFunction && psiElement.name?.contains("main") == true) return false
        if (psiElement in node.parents || psiElement == node) return false
        return true
    }

    data class ScopeComponent(
        val psiElement: PsiElement,
        val declaration: DeclarationDescriptor?,
        val type: KotlinType
    ) {

//        fun makeExpressionToInsertFromPsiElement(randomInstancesGenerator: RandomInstancesGenerator): ScopeComponent {
//            val expressionToCall =
//                when (declaration) {
//                    is PropertyDescriptor -> {
//                        declaration.name.asString()
//                    }
//                    is ParameterDescriptor -> {
//                        declaration.name.asString()
//                    }
//                    is VariableDescriptor -> {
//                        declaration.name.asString()
//                    }
//                    is FunctionDescriptor -> {
//                        generateCallExpr(declaration, listOf(), randomInstancesGenerator)?.text ?: ""
//                    }
//                    else -> {
//                        psiElement.text
//                    }
//                }.let { Factory.psiFactory.tryToCreateExpression(it) }
//            return ScopeComponent(psiElement, declaration, type)
//        }
//
//        //We are not expecting typeParams
//        private fun generateCallExpr(
//            func: CallableDescriptor,
//            scopeElements: List<ScopeComponent>,
//            rig: RandomInstancesGenerator
//        ): KtExpression? {
//            val name = func.name
//            val valueParams = func.valueParameters.map { vp ->
//                val fromUsages = scopeElements.filter { usage ->
//                    vp.type.getNameWithoutError().trim() == usage.type.getNameWithoutError().trim()
//                }
//                if (fromUsages.isNotEmpty() && Random.getTrue(80)) fromUsages.random().psiElement.text
//                else rig.generateValueOfType(vp.type)
//                //getInsertableExpressions(Pair(it, it.typeReference?.getAbbreviatedTypeOrType()), 1).randomOrNull()
//            }
//            if (valueParams.any { it.isEmpty() }) {
//                return null
//            }
//            val inv = "$name(${valueParams.joinToString()})"
//            return Factory.psiFactory.tryToCreateExpression(inv)
//        }

    }


}