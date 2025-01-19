package org.usvm

import org.jacodb.go.api.GoGlobal
import org.jacodb.go.api.GoInst
import org.jacodb.go.api.GoInstLocation
import org.jacodb.go.api.GoMethod
import org.jacodb.go.api.GoType
import org.usvm.api.UnknownMethodException
import org.usvm.api.UnknownPackageException

const val INIT_FUNCTION = "init"

class GoMethodInfo(
    val variablesCount: Int,
    val argumentsCount: Int,
) {
    override fun toString(): String {
        return "variables: $variablesCount, arguments: $argumentsCount"
    }
}

class GoCall(
    val method: GoMethod,
    val entrypoint: GoInst,
    val parameters: Array<UExpr<out USort>>
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

    fun findMethod(packageName: String, name: String): GoMethod {
        return packagesIndex[packageName]!!.findMethod(name)
    }
}
