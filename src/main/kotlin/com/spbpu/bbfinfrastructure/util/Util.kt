package com.spbpu.bbfinfrastructure.util

import com.intellij.lang.ASTNode
import com.intellij.lang.FileASTNode
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.IncorrectOperationException
import com.spbpu.bbfinfrastructure.project.LANGUAGE
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.psicreator.util.createWhitespace
import com.spbpu.bbfinfrastructure.util.kcheck.asCharSequence
import com.spbpu.bbfinfrastructure.util.kcheck.nextInRange
import com.spbpu.bbfinfrastructure.util.kcheck.nextString
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.PsiDiagnosticUtils
import org.jetbrains.kotlin.js.descriptorUtils.nameIfStandardType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.source.PsiSourceElement
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.types.typeUtil.isUnsignedNumberType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeoutException
import java.util.function.BiPredicate
import kotlin.reflect.KClass

fun <T> Iterable<T>.getAllWithout(el: T): List<T> {
    val list: ArrayList<T> = arrayListOf<T>()
    for (item in this) {
        if (item != el) list.add(item)
    }
    return list
}


fun List<TypeParameterDescriptor>.sortedByTypeParams(): List<TypeParameterDescriptor> {
    return this.sortedWith(typeParamDescComparator)
}


private val typeParamDescComparator = Comparator { t1: TypeParameterDescriptor, t2: TypeParameterDescriptor ->
    if (t1.upperBounds.isEmpty() && t2.upperBounds.isEmpty()) {
        0
    } else {
        val t1Args = t1.upperBounds.flatMap { listOf("$it") + it.getAllTypeParams().map { "${it.type}" } }
        if (t1Args.any { it == "${t2.defaultType}" }) 1 else -1
    }
}

fun DeclarationDescriptor.findPsi(): PsiElement? {
    val psi = (this as? DeclarationDescriptorWithSource)?.source?.getPsi()
    return if (psi == null && this is CallableMemberDescriptor && kind == CallableMemberDescriptor.Kind.FAKE_OVERRIDE) {
        overriddenDescriptors.mapNotNull { it.findPsi() }.firstOrNull()
    } else {
        psi
    }
}

fun SourceElement.getPsi(): PsiElement? = (this as? PsiSourceElement)?.psi

fun KotlinType.replaceTypeArgsToTypes(map: Map<String, String>): String {
    val realType =
        when {
            isTypeParameter() -> map[this.constructor.toString()] ?: map["${this.constructor}?"] ?: this.toString()
            this.isNullable() -> "${this.constructor}?"
            else -> this.constructor.toString()
        }
    val typeParams = this.arguments.map { it.type.replaceTypeArgsToTypes(map) }
    return if (typeParams.isNotEmpty()) "$realType<${typeParams.joinToString()}>" else realType
}

val KotlinType.name: String?
    get() = this.constructor.declarationDescriptor?.name?.asString()

fun ClassDescriptor.isFunInterface(): Boolean {
    if (this.kind != ClassKind.INTERFACE) return false
    return this.unsubstitutedMemberScope.getDescriptorsFiltered { true }.size == 4
}

fun PsiFile.addMain(boxFuncs: List<KtNamedFunction>) {
    val m = java.lang.StringBuilder()
    m.append("fun main(args: Array<String>) {\n")
    for (func in boxFuncs) m.append("println(${func.name}())\n")
    m.append("}")
    val mainFun = KtPsiFactory(this.project).createFunction(m.toString())
    this.add(KtPsiFactory(this.project).createWhiteSpace("\n\n"))
    this.add(mainFun)
}

inline fun <reified T : PsiElement> PsiElement.containsChildOfType(): Boolean = this.node.children().any { it is T }

fun PsiFile.getNodesBetweenWhitespaces(begin: Int, end: Int): List<PsiElement> {
    val resList = mutableListOf<PsiElement>()
    var whiteSpacesCounter = 0
    for (node in getAllPSIDFSChildrenOfType<PsiElement>()) {
        val lineNumber = PsiDiagnosticUtils.atLocation(node).substringAfter('(').substringBefore(',').toInt()
        if (lineNumber in begin..end) {
            resList.add(node)
        }
    }
    return resList
}

fun KtNamedFunction.isUnit() = this.typeReference == null && this.hasBlockBody()

fun KtPsiFactory.createNonEmptyClassBody(body: String): KtClassBody {
    return createClass("class A(){\n$body\n}").body!!
}


fun KtClassOrObject.addPsiToBody(prop: PsiElement): PsiElement? =
    this.body?.addBeforeRBrace(prop) ?: this.add(Factory.psiFactory.createNonEmptyClassBody(prop.text))

fun KtClassBody.addBeforeRBrace(psiElement: PsiElement): PsiElement {
    return this.rBrace?.let { rBrace ->
        val ws = this.addBefore(Factory.psiFactory.createWhiteSpace("\n"), rBrace)
        val res = this.addAfter(psiElement, ws)
        this.addAfter(Factory.psiFactory.createWhiteSpace("\n"), res)
        res
    } ?: psiElement
}

fun KtFile.getAvailableValuesToInsertIn(
    node: PsiElement,
    ctx: BindingContext
): List<Pair<KtExpression, KotlinType?>> {
    val nodeParents = node.parents.toList()
    val parameters = nodeParents
        .filterIsInstance<KtCallableDeclaration>()
        .flatMap { it.valueParameters }
        .filter { it.name != null }
        .map { Factory.psiFactory.createExpression(it.name ?: "") to it.typeReference?.getAbbreviatedTypeOrType(ctx) }
        .filter { it.second != null }
    val props = nodeParents
        .flatMap { it.getAllPSIDFSChildrenOfType<PsiElement>().takeWhile { it != node } }
        .filterIsInstance<KtProperty>()
        .filterDuplicates { a: KtProperty, b: KtProperty -> if (a == b) 0 else 1 }
        .filter { it.parents.filter { it is KtBlockExpression }.all { it in nodeParents } }
        .filter { it.name != null }
        .map {
            val kotlinType = it.typeReference?.getAbbreviatedTypeOrType(ctx) ?: it.initializer?.getType(ctx)
            Factory.psiFactory.createExpression(it.name ?: "") to kotlinType
        }
        .filter { it.second != null }
    return parameters + props
}

fun PsiElement.addAfterThisWithWhitespace(psiElement: PsiElement, whiteSpace: String): PsiElement {
    return try {
        val placeToInsert = this
        placeToInsert.add(Factory.javaPsiFactory.createWhitespace())
        val res = placeToInsert.add(psiElement)
        placeToInsert.add(Factory.javaPsiFactory.createWhitespace())
        res
    } catch (e: IncorrectOperationException) {
        this
    }
}


internal fun getSlice(node: PsiElement): Set<KtExpression> {
    val res = mutableSetOf<KtExpression>()
    for (prop in getPropsUntil(node.parent, node)) {
        res.addAll(prop.getAllPSIDFSChildrenOfType())
    }
    //getPropsUntil(node.parent, node).forEach { res.addAll(it.getAllPSIDFSChildrenOfType()) }
    node.getAllParentsWithoutThis().zipWithNext().forEach {
        for (prop in getPropsUntil(it.second, it.first)) res.add(prop)
        //getPropsUntil(it.second, it.first).forEach { res.add(it) }
    }
    return res
}

internal fun PsiElement.getAllParentsWithoutThis(): List<PsiElement> {
    val result = arrayListOf<ASTNode>()
    var node = this.node.treeParent ?: return arrayListOf<PsiElement>()
    while (true) {
        result.add(node)
        if (node.treeParent == null)
            break
        node = node.treeParent
    }
    return result.map { it.psi }
}

fun getPropsUntil(node: PsiElement, until: PsiElement) =
    node.getAllChildren()
        .takeWhile { it != until }
        .filter { it !is KtNamedFunction && it !is KtClassOrObject && it is KtExpression }
        .flatMap { it.getAllPSIDFSChildrenOfType<KtExpression>() }

internal fun <T> List<T>.filterDuplicates(comparator: Comparator<T>): List<T> {
    val res = mutableListOf<T>()
    this.forEach { el -> if (res.all { comparator.compare(it, el) != 0 }) res.add(el) }
    return res
}


fun PsiElement.getPath() = this.parents
    .filter { it is KtNamedFunction || it is KtClassOrObject }
    .map { it as PsiNamedElement }
    .toList().reversed()

//DeclarationDescriptorsUtils
fun DeclarationDescriptor.getParents(): List<DeclarationDescriptor> =
    this.parents
        .filter {
            it !is ModuleDescriptor &&
                    it !is PackageFragmentDescriptor &&
                    it !is PackageViewDescriptor
        }
        .toList()

fun DeclarationDescriptor.getReturnTypeForCallableAndClasses() =
    when (this) {
        is CallableDescriptor -> this.returnType
        is ClassDescriptor -> this.defaultType
        else -> null
    }

fun PsiElement.getAllChildrenWithItself(): List<PsiElement> = listOf(this) + this.getAllChildren()

fun KotlinType.isIterable() =
    this.memberScope.getDescriptorsFiltered { true }.any {
        it.toString().contains("operator fun iterator")
    }

fun PsiElement.getLocationLineNumber()  =
    PsiDiagnosticUtils.atLocation(this).substringAfter('(').substringBefore(',').toInt()

fun PsiFile.getRandomPlaceToInsertNewLine() = this.getRandomPlaceToInsertNewLine(-1)
fun PsiFile.getRandomPlaceToInsertNewLine(fromLine: Int): PsiElement? {
    val lastImportStatementLineNumber = text.split("\n").indexOfLast { it.startsWith("import ") }
    return getAllPSIChildrenOfType<PsiWhiteSpace>()
        .filter { it.text.contains("\n") }
        .filter { it.getLocationLineNumber().let { it > lastImportStatementLineNumber && it > fromLine }}
        .randomOrNull()
}

fun PsiFile.getRandomPlaceToInsertNewLine(fromLine: Int, toLine: Int): PsiElement? {
    val lastImportStatementLineNumber = text.split("\n").indexOfLast { it.startsWith("import ") }
    return getAllPSIChildrenOfType<PsiWhiteSpace>()
        .filter { it.text.contains("\n") }
        .filter { it.getLocationLineNumber().let { it > lastImportStatementLineNumber && it > fromLine && it < toLine }}
        .randomOrNull()
}


fun executeWithTimeout(timeoutInMilliseconds: Long, body: () -> Any?): Any? {
    var result: Any? = null
    val thread = Thread {
        result = try {
            body()
        } catch (e: Throwable) {
            e
        }
    }
    thread.start()
    thread.join(timeoutInMilliseconds)
    var isThreadStopped = false
    while (thread.isAlive) {
        @Suppress("DEPRECATION")
        thread.stop()
        isThreadStopped = true
    }
    when {
        isThreadStopped -> throw TimeoutException()
        result is InvocationTargetException -> throw (result as InvocationTargetException).cause ?: result as Throwable
        else -> return result
    }
}
