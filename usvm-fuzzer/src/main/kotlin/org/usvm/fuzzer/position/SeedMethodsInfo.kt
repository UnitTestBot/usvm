package org.usvm.fuzzer.position

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.usvm.fuzzer.strategy.ChoosingStrategy
import org.usvm.fuzzer.strategy.Selectable

class SeedMethodsInfo(
    private val methodChoosingStrategy: ChoosingStrategy<MethodInfo>
) {

    private val info = HashSet<MethodInfo>()
    private val addedClasses = HashSet<JcClassOrInterface>()

    fun hasClassBeenParsed(jcClass: JcClassOrInterface) = addedClasses.contains(jcClass)

    fun addMethodInfo(
        targetMethod: JcMethod, method: JcMethod, score: Double, numberOfChooses: Int
    ) {
        addedClasses.add(method.enclosingClass)
        info.add(
            MethodInfo(
                targetMethod, method, score, numberOfChooses
            )
        )
    }

    fun getBestMethod(): MethodInfo =
        methodChoosingStrategy.chooseBest(info, 0)

    fun getBestMethod(condition: (JcMethod) -> Boolean): JcMethod? {
        val filteredMethods = info.filter { condition(it.method) }.ifEmpty { return null }
        return methodChoosingStrategy.chooseBest(filteredMethods, 0).method
    }

    open class MethodInfo(
        val targetMethod: JcMethod, val method: JcMethod, override var score: Double, override var numberOfChooses: Int
    ) : Selectable() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MethodInfo

            if (targetMethod != other.targetMethod) return false
            if (method != other.method) return false

            return true
        }

        override fun hashCode(): Int {
            var result = targetMethod.hashCode()
            result = 31 * result + method.hashCode()
            return result
        }

    }
}

