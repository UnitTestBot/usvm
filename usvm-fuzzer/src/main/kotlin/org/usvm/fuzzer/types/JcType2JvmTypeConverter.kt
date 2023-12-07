@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.usvm.fuzzer.types

import org.jacodb.api.*
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.impl.types.signature.*


//fun JcType.convertToJvmType(): JvmType {
//    val typeNameWOGenerics = typeName.substringBefore('<')
//    return when (this) {
//        is JcClassType -> JvmClassRefType(typeNameWOGenerics, nullable, annotations)
//        is JcArrayType -> JvmArrayType(elementType.convertToJvmType(), nullable, annotations)
//        is JcPrimitiveType -> JvmPrimitiveType(typeNameWOGenerics, annotations)
//        is JcTypeVariable -> JvmTypeVariable(symbol, nullable, annotations)
//        is JcUnboundWildcard -> JvmUnboundWildcard
//        else -> error("cant generate jvm type for $typeName")
//    }
//}

//fun JcTypeVariableDeclaration.convertToJvmTypeParameterDeclarationImpl(): JvmTypeParameterDeclaration =
//    JvmTypeParameterDeclarationImpl(
//        symbol,
//        owner,
//        bounds.map { it.convertToJvmType() }
//    )

//fun JcType.getResolvedTypeWithSubstitutions(substitutions: List<Substitution>): JcTypeWrapper =
//    when (this) {
////        is JcArrayType -> {
////            JcArrayTypeImpl(elementType.getResolvedTypeWithSubstitutions(substitutions), nullable, annotations)
////        }
//        is JcClassTypeImpl -> {
//            JcTypeWrapper(
//                type = JcClassTypeImpl(
//                    classpath = classpath,
//                    name = name,
//                    outerType = outerType,
//                    parameters = typeParameters
//                        .map { it.convertToJvmTypeParameterDeclarationImpl() }
//                        .map { typeParam -> substitutions.find { it.typeParam == typeParam }!!.substitution },
//                    nullable = nullable,
//                    annotations = annotations
//                ),
//                substitutions = substitutions
//            )
//        }
//        else -> JcTypeWrapper(this, listOf())
//    }

//fun JcType.getResolvedType(generics: List<JcType>): JcType =
//    when (this) {
//        is JcArrayType -> {
//            JcArrayTypeImpl(elementType.getResolvedType(generics), nullable, annotations)
//        }
//
//        is JcClassTypeImpl -> JcClassTypeImpl(
//            classpath = classpath,
//            name = name,
//            outerType = outerType,
//            substitutor = JcSubstitutorImpl(
//                typeParameters
//                    .map { it.convertToJvmTypeParameterDeclarationImpl() }
//                    .zipToMap(generics.map { it.convertToJvmType() }).toPersistentMap()
//            ),
//            nullable = nullable,
//            annotations = annotations
//        )
//
//        else -> this
//    }

//object JcType2JvmTypeConverter {
//
//    private data class GenericTree(
//        val root: GenericNode
//    )
//
//    private data class GenericNode(
//        val type: String,
//        val parent: GenericNode?,
//        val children: MutableList<GenericNode>
//    ) {
//        override fun toString(): String {
//            return "$type ${children.joinToString(" ")}"
//        }
//    }
//
//    private fun buildGenericTree(type: String): GenericTree {
//        val t = type.filterNot { it.isWhitespace() }
//        val root = t.substringBefore('<')
//        val rootNode = GenericNode(root, null, mutableListOf())
//        val tree = GenericTree(rootNode)
//        var acc = ""
//        var curNode: GenericNode? = rootNode
//        for (ch in t.substringAfter('<')) {
//            if (ch == '<') {
//                val newNode = GenericNode(acc, curNode, mutableListOf())
//                curNode?.children?.add(newNode)
//                curNode = newNode
//                acc = ""
//            } else if (ch == ',') {
//                if (acc.isNotEmpty()) {
//                    val newNode = GenericNode(acc, curNode, mutableListOf())
//                    curNode?.children?.add(newNode)
//                    acc = ""
//                }
//            } else if (ch == '>') {
//                if (acc.isNotEmpty()) {
//                    val newNode = GenericNode(acc, curNode, mutableListOf())
//                    curNode?.children?.add(newNode)
//                    acc = ""
//                }
//                curNode = curNode?.parent
//            } else {
//                acc += ch
//            }
//        }
//        return tree
//    }
//
//    fun convertToJcTypeWrapper(type: String, jcClasspath: JcClasspath): JcTypeWrapper {
//        val genericTree = buildGenericTree(type)
//        val substitutions = mutableListOf<Substitution>()
//        collectSubstitutions(
//            genericTree.root,
//            jcClasspath,
//            substitutions
//        )
//        val jcType = jcClasspath.findTypeOrNull(type.substringBefore('<')) ?: error("Cant find type $type")
//        return jcType.getResolvedTypeWithSubstitutions(substitutions)
//    }
//
//    private fun collectSubstitutions(
//        node: GenericNode,
//        jcClasspath: JcClasspath,
//        substitutions: MutableList<Substitution>
//    ) {
//        if (node.children.isNotEmpty() && node.children.all { it.children.isEmpty() }) {
//            val type = jcClasspath.findTypeOrNull(node.type) as JcClassTypeImpl
//            val generics = node.children.map { jcClasspath.findTypeOrNull(it.type)!! }
//            substitutions.addAll(type.typeParameters
//                .map { it.convertToJvmTypeParameterDeclarationImpl() }
//                .zip(generics.map { it.convertToJvmType() }) { param, sub ->
//                    Substitution(param, sub)
//                }
//            )
//            return
//        }
//        node.children.forEach { collectSubstitutions(it, jcClasspath, substitutions) }
//        val type = jcClasspath.findTypeOrNull(node.type) as JcClassTypeImpl
//        val generics = node.children.map { jcClasspath.findTypeOrNull(it.type)!! }
//        substitutions.addAll(
//            type.typeParameters
//                .map { it.convertToJvmTypeParameterDeclarationImpl() }
//                .zip(generics.map { it.convertToJvmType() }) { param, sub ->
//                    Substitution(param, sub)
//                }
//        )
//        return
//    }
//}

data class Substitution(
    val typeParam: JcTypeVariableDeclaration,
    val substitution: JcTypeWrapper
)

