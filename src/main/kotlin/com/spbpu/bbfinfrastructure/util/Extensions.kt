package com.spbpu.bbfinfrastructure.util

import com.intellij.lang.ASTNode
import com.intellij.lang.FileASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.spbpu.bbfinfrastructure.project.LANGUAGE
import com.spbpu.bbfinfrastructure.psicreator.util.Factory
import com.spbpu.bbfinfrastructure.util.kcheck.asCharSequence
import com.spbpu.bbfinfrastructure.util.kcheck.nextInRange
import com.spbpu.bbfinfrastructure.util.kcheck.nextString
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.js.descriptorUtils.nameIfStandardType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.types.typeUtil.isUnsignedNumberType
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.function.BiPredicate
import kotlin.reflect.KClass
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.kotlinFunction

fun KtProperty.getLeft(): List<PsiElement> =
    if (this.allChildren.toList().any { it.node.elementType.index.toInt() == 179 }) this.allChildren.toList()
        .takeWhile { it.node.elementType.index.toInt() != 179 }
    else listOf()

fun KtProperty.getLeftIdentifier(): PsiElement? =
    this.allChildren.toList().first { it.node.elementType.index.toInt() == 141 }

fun KtProperty.getRight(): List<PsiElement> =
    if (this.allChildren.toList().any { it.node.elementType.index.toInt() == 179 }) this.allChildren.toList()
        .takeLastWhile { it.node.elementType.index.toInt() != 179 }
    else listOf()

fun PsiElement.getAllChildrenOfCurLevel(): List<PsiElement> = this.node.getAllChildrenOfCurLevel().map { it.psi }

fun ASTNode.getAllChildrenOfCurLevel(): Array<ASTNode> = this.getChildren(TokenSet.ANY)
fun ASTNode.getAllChildrenNodes(): ArrayList<ASTNode> {
    val result = ArrayList<ASTNode>()
    var level = 1
    var childrens = this.getAllChildrenOfCurLevel()
    while (childrens.isNotEmpty()) {
        childrens.forEach { result.add(it) }
        ++level
        childrens = this.getAllChildrenOfTheLevel(level).toTypedArray()
    }
    return result
}

fun ASTNode.getAllParents(): ArrayList<ASTNode> {
    val result = arrayListOf<ASTNode>()
    var node = this
    while (true) {
        result.add(node)
        if (node.treeParent == null)
            break
        node = node.treeParent
    }
    return result
}

fun ASTNode.getAllParentsWithoutNode(): ArrayList<ASTNode> {
    val result = arrayListOf<ASTNode>()
    var node = this.treeParent ?: return arrayListOf<ASTNode>()
    while (true) {
        result.add(node)
        if (node.treeParent == null)
            break
        node = node.treeParent
    }
    return result
}


fun <T> Iterable<T>.getAllWithout(index: Int): List<T> {
    val list: ArrayList<T> = arrayListOf<T>()
    var count = 0
    for (item in this) {
        if (count++ != index) list.add(item)
    }
    return list
}

fun <T> Iterable<T>.getAllWithoutLast(): List<T> {
    var size = 0
    for (e in this.iterator()) size++
    return getAllWithout(size - 1)
}

fun <ASTNode> List<ASTNode>.filterRowPsiWhiteSpaces(): List<ASTNode> {
    if (this.isEmpty()) return listOf()
    val res = arrayListOf<ASTNode>()
    res.add(this[0])
    for (i in 1 until this.size) {
        if (!(this[i] is PsiWhiteSpace && this[i - 1] is PsiWhiteSpace))
            res.add(this[i])
    }
    return res
}

fun List<com.intellij.lang.ASTNode>.getAllChildren(): List<com.intellij.lang.ASTNode> {
    if (this.isEmpty()) return listOf()
    val res = arrayListOf<com.intellij.lang.ASTNode>()
    for (node in this) {
        node.getAllChildrenOfCurLevel().forEach { res.add(it) }
    }
    return res
}

fun List<com.intellij.lang.ASTNode>.getAllChildrenOfTheLevel(level: Int): List<com.intellij.lang.ASTNode> {
    if (this.isEmpty())
        return listOf()
    var res = this
    for (i in 1 until level)
        res = res.getAllChildren()
    return res
}

fun FileASTNode.getAllChildrenOfTheLevel(level: Int): List<com.intellij.lang.ASTNode> {
    var res = this.getAllChildrenOfCurLevel().toList()
    for (i in 1 until level)
        res = res.getAllChildren()
    return res
}

fun com.intellij.lang.ASTNode.getAllChildrenOfTheLevel(level: Int): List<com.intellij.lang.ASTNode> {
    var res = this.getAllChildrenOfCurLevel().toList()
    for (i in 1 until level)
        res = res.getAllChildren()
    return res
}

fun <T> Iterable<T>.filterWithNext(predicate: (T, T) -> Boolean): List<T> {
    val dest = ArrayList<T>()
    for (i in 0 until this.count() - 1) {
        if (!predicate(this.elementAt(i), this.elementAt(i + 1)))
            dest.add(this.elementAt(i))
    }
    return dest
}

fun PsiElement.replaceChild(replacing: PsiElement, replacement: PsiElement) {
    replacing.replaceThis(replacement)
}

fun PsiElement.replaceThis(replacement: PsiElement) {
    for (p in this.node.getAllParentsWithoutNode()) {
        try {
            p.replaceChild(this.node, replacement.node)
            return
        } catch (e: AssertionError) {
        }
    }
}

fun KtNamedFunction.getSignature(): String {
    val sign = StringBuilder()
    sign.append(this.name + "(")
    for (p in this.valueParameters.getAllWithout(this.valueParameters.size - 1)) {
        sign.append(p.typeReference?.text + ", ")
    }
    this.valueParameters.lastOrNull()?.let {
        sign.append(it.typeReference?.text)
    }
    sign.append(")")
    return sign.toString()
}

fun getClassWithName(projectFiles: List<KtFile>, name: String): KtClass? {
    for (f in projectFiles) {
        f.node.getAllChildrenNodes()
            .filter { it.elementType == KtNodeTypes.CLASS }
            .map { it.psi as KtClass }
            .find { it.fqName?.asString() == name }
            ?.let { return it }
    }
    return null
}

fun KtNamedFunction.initBodyByTODO(psiFactory: KtPsiFactory) {
    if (!this.hasBody()) {
        return
    } else if (this.hasBlockBody()) {
        if (this.typeReference == null)
            replaceReturnValueTypeOnUnit(psiFactory)
        val eq = psiFactory.createEQ()
        val space = psiFactory.createWhiteSpace(" ")
        val todo = psiFactory.createExpression("TODO()")
        this.node.removeChild(this.bodyExpression!!.node)
        this.add(eq)
        this.add(space)
        this.add(todo)
    } else {
        val todo = psiFactory.createExpression("TODO()")
        this.bodyExpression!!.replaceThis(todo)
    }
}

fun KtProperty.initByTODOWithAnyType(psiFactory: KtPsiFactory) {
    val todo = psiFactory.createExpression("TODO()")
    val anyType = psiFactory.createType("Any")
    this.initializer = todo
    this.typeReference = anyType
}

fun KtProperty.initByTODO(psiFactory: KtPsiFactory) {
    val todo = psiFactory.createExpression("TODO()")
    val anyType = psiFactory.createType("Any")
    this.initializer = todo
    if (this.typeReference == null)
        this.typeReference = anyType
}

fun KtNamedFunction.replaceReturnValueTypeOnAny(psiFactory: KtPsiFactory) {
    val anyType = psiFactory.createType("Any")
    this.typeReference = anyType
}

fun KtNamedFunction.replaceReturnValueTypeOnUnit(psiFactory: KtPsiFactory) {
    val anyType = psiFactory.createType("Unit")
    this.typeReference = anyType
}


fun ASTNode.getAllChildrenOfType(type: IElementType): List<ASTNode> =
    this.getAllChildrenNodes().filter { it.elementType == type }

fun ASTNode.getAllDFSChildren(): List<ASTNode> {
    val res = mutableListOf(this)
    for (child in this.getChildren(TokenSet.ANY)) {
        child.getAllDFSChildren().forEach { res.add(it) }
    }
    return res
}

fun ASTNode.getAllDFSChildrenWithoutItSelf(): List<ASTNode> {
    val res = mutableListOf<ASTNode>()
    for (child in this.getChildren(TokenSet.ANY)) {
        child.getAllDFSChildrenWithoutItSelf1().forEach { res.add(it) }
    }
    return res
}

private fun ASTNode.getAllDFSChildrenWithoutItSelf1(): List<ASTNode> {
    val res = mutableListOf(this)
    for (child in this.getChildren(TokenSet.ANY)) {
        child.getAllDFSChildrenWithoutItSelf1().forEach { res.add(it) }
    }
    return res
}

fun PsiElement.getAllChildrenOfType(type: IElementType): List<ASTNode> =
    this.node.getAllChildrenNodes().filter { it.elementType == type }

inline fun <reified T : PsiElement> PsiElement.getAllPSIChildrenOfType(): List<T> =
    this.node.getAllChildrenNodes().asSequence().filter { it.psi is T }.map { it.psi as T }.toList()

inline fun <reified T : PsiElement, reified U : PsiElement> PsiElement.getAllPSIChildrenOfTwoTypes(): List<PsiElement> =
    this.node.getAllChildrenNodes().asSequence().filter { it.psi is T || it.psi is U }.map { it.psi }.toList()

inline fun <reified T : PsiElement, reified U : PsiElement, reified S : PsiElement>
        PsiElement.getAllPSIChildrenOfThreeTypes(): List<PsiElement> =
    this.node.getAllChildrenNodes().asSequence().filter { it.psi is T || it.psi is U || it.psi is S }.map { it.psi }
        .toList()

inline fun <reified T : PsiElement, reified U : PsiElement, reified S : PsiElement, reified R : PsiElement>
        PsiElement.getAllPSIChildrenOfFourTypes(): List<PsiElement> =
    this.node.getAllChildrenNodes().asSequence().filter { it.psi is T || it.psi is U || it.psi is S || it.psi is R }
        .map { it.psi }
        .toList()


inline fun <reified T : PsiElement, reified U : PsiElement, reified S : PsiElement, reified R : PsiElement, reified K>
        PsiElement.getAllPSIChildrenOfFiveTypes(): List<PsiElement> =
    this.node.getAllChildrenNodes().asSequence()
        .filter { it.psi is T || it.psi is U || it.psi is S || it.psi is R || it.psi is K }
        .map { it.psi }
        .toList()

inline fun <reified T : PsiElement, reified U : PsiElement>
        PsiElement.getAllPSIChildrenOfTwoTypes(crossinline filterFun: (PsiElement) -> Boolean): List<PsiElement> =
    this.node.getAllChildrenNodes().asSequence().filter { it.psi is T || it.psi is U }.map { it.psi }
        .filter { filterFun(it) }.toList()

inline fun <reified T : PsiElement> PsiElement.getAllPSIChildrenOfType(crossinline filterFun: (T) -> Boolean): List<T> =
    this.node.getAllChildrenNodes().asSequence().filter { it.psi is T }.map { it.psi as T }.filter { filterFun(it) }
        .toList()

inline fun <reified T : PsiElement> PsiElement.getAllPSIChildrenOfTypeOfFirstLevel(): List<T> =
    this.node.getAllChildrenOfCurLevel().asSequence().filter { it.psi is T }.map { it.psi as T }.toList()

inline fun <reified T : PsiElement> PsiElement.getAllPSIDFSChildrenOfType(): List<T> =
    this.node.getAllDFSChildren().asSequence().filter { it.psi is T }.map { it.psi as T }.toList()

inline fun <reified T : PsiElement> PsiElement.getAllPSIDFSChildrenOfTypeWithoutItself(): List<T> =
    this.node.getAllDFSChildrenWithoutItSelf().asSequence().filter { it.psi is T }.map { it.psi as T }.toList()

inline fun <reified T : PsiElement> PsiElement.getFirstParentOfType(): T? =
    this.node.getAllParentsWithoutNode().map { it.psi }.filter { it is T }.firstOrNull() as T?

inline fun <reified T : PsiElement> PsiElement.getLastParentOfType(): T? =
    this.node.getAllParentsWithoutNode().map { it.psi }.filter { it is T }.lastOrNull() as T?

fun PsiElement.debugPrint() {
    println("---BEGIN PSI STRUCTURE---")
    debugPrint(0)
    println("---END PSI STRUCTURE---")
}

fun PsiElement.debugPrint(indentation: Int) {
    println("|".repeat(indentation) + toString())
    for (child in children)
        child.debugPrint(indentation + 1)
    if (children.isEmpty())
        println("|".repeat(indentation + 1) + "'$text'")
}

fun PsiElement.isBlockExpr() = this.allChildren.first?.node?.elementType == KtTokens.LBRACE &&
        this.allChildren.last?.node?.elementType == KtTokens.RBRACE

fun getRandomBoolean(n: Int = 1): Boolean {
    var res = true
    repeat(n) { res = res && Random().nextBoolean() }
    return res
}

fun getTrueWithProbability(probability: Int): Boolean = Random().nextInt(100) in 0..probability

fun Random.getRandomVariableName(length: Int = 5): String =
    this.nextString(('a'..'z').asCharSequence(), length, length + 1)

fun kotlin.random.Random.getRandomVariableName(length: Int = 5): String =
    Random().nextString(('a'..'z').asCharSequence(), length, length + 1)

fun String.isSubstringOf(other: String): Boolean {
    val m = this.length
    val n = other.length

    for (i in 0..n - m) {
        var j = 0
        while (j < m) {
            if (other[i + j] != this[j]) {
                break
            }
            j++
        }
        if (j == m)
            return true
    }
    return false
}

fun removeMainFromFiles(dir: String) {
    val files = mutableListOf<File>()
    Files.find(Paths.get(dir), Int.MAX_VALUE, BiPredicate { _, u -> u.isRegularFile })
        .forEach { files.add(it.toFile()) }
    files.forEach {
        if (!it.name.contains("mutated"))
            return@forEach
        var text = BufferedReader(FileReader(it)).readText()
        text = text.removeSuffix(
            "fun main(args: Array<String>) {\n" +
                    "    println(box())\n" +
                    "}"
        )
        val fooStream = FileOutputStream(it, false)
        fooStream.write(text.toByteArray())
        fooStream.close()
    }
}

fun <T, R : Comparable<R>> List<T>.filterDuplicatesBy(f: (T) -> R): List<T> {
    val list1 = this.zip(this.map(f))
    val res = mutableListOf<Pair<T, R>>()
    for (i in 0 until size) {
        val item = list1[i].second
        if (res.all { it.second != item }) res.add(list1[i])
    }
    return res.map { it.first }
}

fun <T> List<T>.subList(range: Pair<Int, Int>) = this.subList(range.first, range.second)
fun <T> List<T>.subList(range: IntRange) = this.subList(range.first, range.last)

fun KtBlockExpression.addProperty(prop: KtProperty): PsiElement? {
    val factory = KtPsiFactory(this.project)
    val firstStatement = this.firstStatement ?: this.rBrace ?: return null
    addBefore(factory.createWhiteSpace("\n"), firstStatement)
    val res = addBefore(prop, firstStatement)
    addBefore(factory.createWhiteSpace("\n"), firstStatement)
    return res
}

fun KtFile.getBoxFuncs(): List<KtNamedFunction>? =
    this.getAllPSIChildrenOfType { it.text.contains(Regex("""fun box\d*\(""")) }

fun PsiFile.addAtTheEnd(psiElement: PsiElement): PsiElement {
    return this.getAllPSIDFSChildrenOfType<PsiElement>().last().parent.let {
        it.add(Factory.psiFactory.createWhiteSpace("\n\n"))
        val res = it.add(psiElement)
        it.add(Factory.psiFactory.createWhiteSpace("\n\n"))
        res
    }
}


fun PsiFile.addToTheTop(psiElement: PsiElement): PsiElement {
    val firstChild = this.allChildren.first!!
    firstChild.add(Factory.psiFactory.createWhiteSpace("\n"))
    val res = firstChild.add(psiElement)
    firstChild.add(Factory.psiFactory.createWhiteSpace("\n"))
    return res
}

fun KtFile.addImport(import: String, isAllUnder: Boolean) {
    val importDirective = Factory.psiFactory.createImportDirective(ImportPath(FqName(import), isAllUnder))
    this.addImport(importDirective)
}

fun KtFile.addImport(importDir: KtImportDirective) {
    if (this.importDirectives.any { it.text == importDir.text }) return
    this.importList?.add(KtPsiFactory(this.project).createWhiteSpace("\n"))
    this.importList?.add(importDir)
    this.importList?.add(KtPsiFactory(this.project).createWhiteSpace("\n"))
}

fun String.getFileLanguageIfExist(): LANGUAGE? {
    val name = this.split("\n").first().trim().substringAfterLast(' ')
    if (name.endsWith(".kt")) return LANGUAGE.KOTLIN
    if (name.endsWith(".java")) return LANGUAGE.JAVA
    return null
}

fun String.filterLines(cond: (String) -> Boolean): String =
    this.lines().filter { cond(it) }.joinToString("\n")

fun String.filterNotLines(cond: (String) -> Boolean): String =
    this.lines().filterNot { cond(it) }.joinToString("\n")

fun kotlin.random.Random.getTrue(prob: Int) = Random().nextInRange(0, 100) < prob

fun KtFile.findFunByName(name: String): KtNamedFunction? =
    this.getAllPSIChildrenOfType<KtNamedFunction>().find { it.name == name }

fun KotlinType.isPrimitiveTypeOrNullablePrimitiveTypeOrString(): Boolean {
    val descriptor = this.constructor.declarationDescriptor
    return descriptor is ClassDescriptor &&
            (KotlinBuiltIns.isPrimitiveClass((descriptor as ClassDescriptor?)!!)
                    || this.isUnsignedNumberType()
                    || this.nameIfStandardType?.asString() == "String")
}

fun KotlinType.isErrorType(): Boolean = this.isError || this.arguments.any { it.type.isErrorType() }

fun KotlinType.getNameWithoutError(): String {
    val thisName =
        when (this) {
            is ErrorType -> this.presentableName
            is UnresolvedType -> this.presentableName
            else -> "${this.name}"
        }
    val argsName =
        if (arguments.isNotEmpty()) "<${this.arguments.joinToString { it.type.getNameWithoutError() }}>"
        else ""
    return thisName + argsName
}

fun KotlinType.supertypesWithoutAny(): Collection<KotlinType> =
    if (this.supertypes().size <= 1) listOf()
    else this.supertypes().getAllWithoutLast()

@Deprecated("")
fun <T : Any> List<Any>.flatten(type: KClass<T>): List<T> {
    val res = mutableListOf<T>()
    this.forEach { el ->
        if (type.isInstance(el)) res.add(el as T)
        if (el is List<*>) (el as List<Any>).flatten(type).forEach { res.add(it) }
    }
    return res
}


inline fun <reified T : Any> List<Any>.flatten(): List<T> = this.flatten(T::class)

fun PsiElement.getAllChildren(): List<PsiElement> = this.node.getAllChildrenNodes().map { it.psi }

fun PsiFile.contains(cond: (PsiElement) -> Boolean) = this.getAllChildren().any { cond(it) }

fun KotlinType.getAllTypeParams(): List<TypeProjection> =
    this.arguments + this.arguments.flatMap { it.type.getAllTypeParams() }

fun KotlinType.getAllTypeParamsWithItself(): List<TypeProjection> =
    if (this.isTypeParameter()) listOf(this.asTypeProjection()) + this.getAllTypeParams()
    else this.getAllTypeParams()

fun KotlinType.hasTypeParam(): Boolean =
    this.isTypeParameter() || getAllTypeParams().any { it.type.isTypeParameter() }

fun KotlinType.isKType(): Boolean =
    constructor.declarationDescriptor?.name?.asString()
        ?.let { it == "KClass" || it.startsWith("KProperty") || it.startsWith("KFunction") }
        ?: false

fun KotlinType.isAbstractClass(): Boolean =
    (this.constructor.declarationDescriptor as? ClassDescriptor)?.modality == Modality.ABSTRACT

fun KotlinType.replaceTypeOrRandomSubtypeOnTypeParam(typeParams: List<String>): String {
    val typeParamsWithoutBounds = typeParams.map { it.substringBefore(':') }
    val withVariantTypeParams = typeParamsWithoutBounds.filter { it.contains("in ") || it.contains("out ") }
    if (kotlin.random.Random.getTrue(30) && withVariantTypeParams.isNotEmpty())
        return withVariantTypeParams.random().substringAfter("in ").substringAfter("out ")
    val withoutVariantTypeParams = typeParamsWithoutBounds.filter { !it.contains("in ") && !it.contains("out ") }
    if (withoutVariantTypeParams.isEmpty()) return "$this"
    val typeParamsWithoutBoundsAndModifiers =
        withoutVariantTypeParams.map { it.substringBefore(':').substringAfter("in ").substringAfter("out ") }
    if (kotlin.random.Random.getTrue(20)) return typeParamsWithoutBoundsAndModifiers.random()
    return if (this.arguments.isNotEmpty()) {
        this.toString().substringBefore('<') + this.arguments.joinToString(prefix = "<", postfix = ">") {
            if (kotlin.random.Random.getTrue(40)) typeParamsWithoutBoundsAndModifiers.random() else it.toString()
        }
    } else this.toString()
}

fun KotlinType.getMinModifier() =
    this
        .let { listOf(it) + it.getAllTypeParams().map { it.type } }
        .map { it.constructor.declarationDescriptor }
        .mapNotNull { (it as? ClassDescriptor)?.visibility }
        .minWithOrNull { t: DescriptorVisibility, t2: DescriptorVisibility -> t.compareTo(t2) ?: 0 }
        ?.name ?: "public"


fun compareDescriptorVisibilitiesAsStrings(v1: String, v2: String): Int {
    return when {
        v1 == v2 -> 0
        v1 == "public" -> -1
        v2 == "public" -> 1
        v1 == "" -> -1
        v2 == "" -> 1
        v1 == "internal" -> -1
        v2 == "internal" -> 1
        else -> 0
    }
}

fun generateVisibilityModifier(minModifier: String) =
    when (minModifier) {
        "public" -> listOf("internal", "private", "public").random()
        "internal" -> listOf("internal", "private").random()
        "private" -> "private"
        else -> listOf("internal", "private", "public").random()
    }

fun KtProperty.getVisibility() =
    when {
        this.text.contains("public") -> "public"
        this.text.contains("private") -> "private"
        this.text.contains("internal") -> "internal"
        this.text.contains("protected") -> "protected"
        else -> "public"
    }

fun KtNamedFunction.getReturnType(context: BindingContext): KotlinType? =
    this.typeReference?.getAbbreviatedTypeOrType(context) ?: this.initializer?.getType(context)

fun KtProperty.getPropertyType(context: BindingContext): KotlinType? =
    this.typeReference?.getAbbreviatedTypeOrType(context) ?: this.initializer?.getType(context)

fun KtForExpression.getLoopParameterType(context: BindingContext): KotlinType? {
    val loopRangeType = this.loopRange?.getType(context) ?: return null
    val memberScope = loopRangeType.memberScope.getDescriptorsFiltered { true }
    val member = memberScope.firstOrNull { it.name.asString() == "first" } as? PropertyDescriptor
        ?: memberScope.firstOrNull { it.name.asString() == "get" } as? FunctionDescriptor
    return member?.returnType
}

fun Method.isDeprecated(): Boolean =
    if (declaringClass == String::class.java) {
        val isDepInKotlin =
            String::class.functions.find { it.name == name }?.annotations?.any { it.annotationClass == Deprecated::class }
                ?: false
        val isDepInJava = this.annotations.any { it.annotationClass.simpleName == "Deprecated" }
        isDepInJava || isDepInKotlin
    } else {
        this.annotations.any { it.annotationClass.simpleName == "Deprecated" }
    }

fun <T> List<T>.randomOrNullWithIndex(): Pair<T, Int>? = try {
    val randomIndex = kotlin.random.Random.nextInt(0, size)
    get(randomIndex) to randomIndex
} catch (e: Throwable) {
    null
}


