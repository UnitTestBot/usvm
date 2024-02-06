package org.usvm.fuzzer.position

import org.jacodb.api.*
import org.jacodb.impl.features.classpaths.virtual.JcVirtualField
import org.jacodb.impl.features.classpaths.virtual.JcVirtualFieldImpl
import org.objectweb.asm.Opcodes
import org.usvm.fuzzer.strategy.ChoosingStrategy
import org.usvm.fuzzer.strategy.Selectable
import org.usvm.instrumentation.util.getTypename

class SeedFieldsInfo(
    private val fieldChoosingStrategy: ChoosingStrategy<FieldInfo>
) {

    private val info = HashSet<FieldInfo>()
    private val addedClasses = HashSet<JcClassOrInterface>()

    fun hasClassBeenParsed(jcClass: JcClassOrInterface) = addedClasses.contains(jcClass)

    fun addFieldInfo(
        targetMethod: JcMethod,
        jcField: JcField,
        score: Double,
        numberOfChooses: Int
    ) {
        addedClasses.add(jcField.enclosingClass)
        info.add(FieldInfo(targetMethod, jcField, score, numberOfChooses))
    }

    fun addArgInfo(
        targetMethod: JcMethod,
        argPosition: Int,
        argType: JcType,
        score: Double,
        numberOfChooses: Int
    ) {
        if (argType is JcClassType) {
            addedClasses.add(argType.jcClass)
        }
        val jcVirtualField =
            JcVirtualFieldImpl("arg_$argPosition", Opcodes.ACC_PUBLIC, argType.getTypename())
                .also { it.bind(targetMethod.enclosingClass) }
        info.add(
            FieldInfo(
                targetMethod,
                jcVirtualField,
                score,
                numberOfChooses
            )
        )
    }

    fun getFieldInfo(jcField: JcField) = info.find { it.jcField == jcField }

    fun getBestField() = fieldChoosingStrategy.chooseBest(info, 0)

    fun getBestField(condition: (JcField) -> Boolean): JcField? {
        val filteredFields = info.filter { condition(it.jcField) }.ifEmpty { return null }
        return fieldChoosingStrategy.chooseBest(filteredFields, 0).jcField
    }

    fun getBestField(condition: (FieldInfo) -> Boolean): FieldInfo? {
        val filteredFields = info.filter { condition(it) }.ifEmpty { return null }
        return fieldChoosingStrategy.chooseBest(filteredFields, 0)
    }

    fun getWorstField() = info.random()

    open class FieldInfo(
        val jcTargetMethod: JcMethod,
        val jcField: JcField,
        override var score: Double,
        override var numberOfChooses: Int
    ) : Selectable() {

        fun isArg() = jcField is JcVirtualField
        fun getArgPosition() = if (!isArg()) -1 else jcField.name.substringAfter("arg_").toInt()
        override fun toString(): String =
            "Field: ${jcField.name} || Score: $score || NumberOfChoices: $numberOfChooses"


        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FieldInfo

            if (jcField.name != other.jcField.name) return false
            if (jcField.enclosingClass.name != other.jcField.enclosingClass.name) return false

            return true
        }

        override fun hashCode(): Int {
            var result = jcTargetMethod.hashCode()
            if (jcField is JcVirtualField) {
                result = 31 * result + jcField.name.hashCode() + jcField.enclosingClass.hashCode()
            } else {
                result = 31 * result + jcField.hashCode()
            }
            return result
        }


    }

}