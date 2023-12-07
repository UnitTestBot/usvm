package org.usvm.fuzzer.types

import org.jacodb.api.*
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.toType
import org.jacodb.impl.types.asJcDeclaration
import org.jacodb.impl.types.typeParameters
import org.usvm.instrumentation.util.toJavaClass
import org.usvm.instrumentation.util.toJcClass
import java.lang.reflect.ParameterizedType
import java.util.*


class JcGenericGeneratorImpl(
    private val jcClasspath: JcClasspath
) : JcGenericGenerator {

    //TODO now we replacing only for bound
//    override fun replaceGenericParametersForType(type: JcType): JcTypeWrapper =
//        JcTypeWrapper(
//            type,
//            type.getTypeParameters().map {
//                Substitution(
//                    it.convertToJvmTypeParameterDeclarationImpl(),
//                    it.bounds.firstOrNull()?.convertToJvmType() ?: jcClasspath.objectType.convertToJvmType()
//                )
//            }
//        )


//    override fun replaceGenericParametersForMethod(resolvedClassType: JcTypeWrapper, method: JcMethod): Pair<JcTypedMethod, List<Substitution>> {
//        val methodSubstitutions = method.typeParameters.map {
//            Substitution(
//                it,
//                it.bounds?.firstOrNull() ?: jcClasspath.objectType.convertToJvmType()
//            )
//        }
//        return JcTypedMethodImpl(
//            method.enclosingClass.toType(),
//            method,
//            JcSubstitutorImpl((methodSubstitutions + resolvedClassType.substitutions).toMap())
//        ) to methodSubstitutions
//    }

//private fun List<Substitution>.toMap(): PersistentMap<JvmTypeParameterDeclaration, JvmType> =
//    associate { it.typeParam to it.substitution }.toPersistentMap()


    private fun JcType.getTypeParameters(): List<JcTypeVariableDeclaration> =
        when (this) {
            is JcArrayType -> this.elementType.getTypeParameters()
            is JcClassType -> this.typeParameters
            else -> listOf()
        }

    override fun replaceGenericParametersForType(type: JcType): JcTypeWrapper {
        if (type.getTypeParameters().isEmpty()) return JcTypeWrapper(type, listOf())
        val substitutions = type.getTypeParameters().map { getSubstitutionForTypeParam(it) }
        return JcTypeWrapper(
            type,
            substitutions
        )
    }

    private fun getSubstitutionForTypeParam(jcTypeVariableDeclaration: JcTypeVariableDeclaration): Substitution {
        val replacementForTypeParam = JcClassTable.getRandomSubclassOf(jcTypeVariableDeclaration.bounds.map { it.jcClass })?.toType() ?: jcClasspath.objectType
        return Substitution(
            jcTypeVariableDeclaration,
            replaceGenericParametersForType(replacementForTypeParam)
        )
    }

    override fun replaceGenericParametersForMethod(
        resolvedClassType: JcTypeWrapper,
        method: JcMethod
    ): Pair<JcMethod, List<Substitution>> {
        if (method.typeParameters.isEmpty()) return method to listOf()
        val substitutions = method.typeParameters
            .map { it.asJcDeclaration(method) }
            .map { getSubstitutionForTypeParam(it) }
        return method to substitutions
    }

    fun replaceGenericsForSubtypeOf(jcClassOrInterface: JcClassOrInterface, subtype: JcTypeWrapper, classLoader: ClassLoader): JcTypeWrapper {
        //TODO simple scheme is implemented when generics mapping directly
        //It may work incorrect
        val implementerTp = jcClassOrInterface.toType().typeParameters
        val subTypeTp = subtype.type.getTypeParameters()
        val subs = if (implementerTp.size == subTypeTp.size) {
            implementerTp.zip(subTypeTp).map { (implementerTp, subtypeTp) ->
                val substitution = subtype.substitutions.find { it.typeParam.equalTo(subtypeTp) }
                if (substitution != null) {
                    Substitution(implementerTp, substitution.substitution)
                } else {
                    getSubstitutionForTypeParam(implementerTp)
                }
            }
        } else {
            implementerTp.map { getSubstitutionForTypeParam(it) }
        }
        return JcTypeWrapper(jcClassOrInterface.toType(), subs)

//        println("LOLOL")
//        val pathToSubtype = getPathTo(jcClassOrInterface, (subtype.type as JcClassType).jcClass).dropLast(1).reversed()
//        //Deal with generics mapping
//        val generics = mutableListOf<Pair<JcTypeWrapper, MutableList<JcTypeVariableDeclaration>>>()
//        var prevImplementer = subtype.type.toJavaClass(classLoader)
//        subtype.type.typeParameters.forEach { tp ->
//            val substitution = subtype.substitutions.find { it.typeParam.equalTo(tp) }
//            if (substitution != null) {
//                generics.add(substitution.substitution to mutableListOf(tp))
//            }
//        }
//        for (implementer in pathToSubtype) {
//            val jImplementer = implementer.toJavaClass(classLoader)
//            val typeArgumentsPassedToPrev =
//                jImplementer.getParamGenericSuperClassAndInterfaces()
//                    .find { it.rawType == prevImplementer }
//                    ?.actualTypeArguments ?: break
//            generics
//        }
//        println()


        return subtype
    }

    fun Class<*>.getParamGenericSuperClassAndInterfaces() =
        (this.genericInterfaces + this.genericSuperclass).filterIsInstance<ParameterizedType>()

    fun JcTypeVariableDeclaration.equalTo(other: JcTypeVariableDeclaration): Boolean {
        if (symbol != other.symbol) return false
        if (owner != other.owner) return false
        return true
    }

    private fun getPathTo(curClass: JcClassOrInterface, targetClass: JcClassOrInterface): List<JcClassOrInterface> {
        val queue: Queue<JcClassOrInterface> = LinkedList()
        val visited: MutableSet<JcClassOrInterface> = HashSet()
        val parentMap: MutableMap<JcClassOrInterface, JcClassOrInterface?> = HashMap()

        queue.offer(curClass)
        visited.add(curClass)
        parentMap[curClass] = null

        while (!queue.isEmpty()) {
            val current = queue.poll()
            visited.add(current)
            if (current == targetClass) {
                return reconstructPath(parentMap, targetClass) ?: emptyList()
            }
            (listOf(current.superClass) + current.interfaces).filterNotNull().forEach { parent ->
                if (!visited.contains(parent)) {
                    queue.offer(parent)
                    parentMap[parent] = current
                }
            }
        }
        return emptyList()
    }

    private fun reconstructPath(parentMap: Map<JcClassOrInterface, JcClassOrInterface?>, target: JcClassOrInterface): List<JcClassOrInterface>? {
        val path = ArrayList<JcClassOrInterface>()
        var current: JcClassOrInterface? = target
        while (current != null) {
            path.add(0, current)
            current = parentMap[current]
        }
        return if (path.size > 1) path else null
    }

    fun getRandomTypeForReplacement(typeVariableDeclaration: JcTypeVariableDeclaration): JcType =
        typeVariableDeclaration.bounds.firstOrNull() ?: jcClasspath.objectType


}