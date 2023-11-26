package org.usvm

import org.jacodb.go.api.GoGlobal
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoInstLocation
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoType
import org.usvm.api.UnknownMethodException
import org.usvm.api.UnknownPackageException
import org.usvm.util.isInit
import org.usvm.util.isOsInit

class GoMethodInfo(
    val variablesCount: Int,
    val argumentsCount: Int,
    val arguments: Array<UExpr<out USort>>
) {
    override fun toString(): String {
        return "variables: $variablesCount, arguments: $argumentsCount"
    }
}

class GoCall(
    val method: GoMethod,
    val entrypoint: GoInst,
)

class GoPackage(
    val name: String,
    val methods: List<GoMethod>,
    val globals: List<GoGlobal>,
    val types: Map<String, GoType>
) {
    private val methodsIndex: Map<String, GoMethod> = methods.associateBy { it.metName }

    fun findMethod(name: String): GoMethod {
        return methodsIndex[name] ?: throw UnknownMethodException(name)
    }
}

class GoProgram(packages: List<GoPackage>) {
    val globals = packages.flatMap { it.globals }
    val types = packages.flatMap { it.types.entries }.associate { it.key to it.value }

    private val packagesIndex: Map<String, GoPackage> = packages.associateBy { it.name }

    fun findPackage(name: String): GoPackage {
        return packagesIndex[name] ?: throw UnknownPackageException(name)
    }

    fun findMethod(location: GoInstLocation, name: String): GoMethod {
        if (name.contains('.') && !name.contains('(')) {
            val split = name.split('.')
            val packageName = split[0]
            val methodName = split[1]
            return findPackage(packageName).findMethod(methodName)
        }

        return findPackage(location.method.packageName).findMethod(name)
    }

    fun findInitMethods(packageName: String): List<GoMethod> {
        return packagesIndex[packageName]!!.methods.filter { it.isInit() }.sortedBy { it.metName }
    }

    fun findOsInitMethods(): List<GoMethod> {
        return packagesIndex.flatMap { it.value.methods }.filter { it.isOsInit() }.sortedBy { it.metName }
    }
}
