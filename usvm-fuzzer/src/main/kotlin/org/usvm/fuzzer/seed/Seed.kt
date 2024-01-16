package org.usvm.fuzzer.seed

import org.jacodb.api.JcClassType
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.impl.cfg.util.isClass
import org.usvm.fuzzer.position.SeedFieldsInfo
import org.usvm.fuzzer.strategy.ChoosingStrategy
import org.usvm.fuzzer.strategy.Selectable
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.getPossiblePathsToField
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
import org.usvm.instrumentation.util.*

data class Seed(
    val targetMethod: JcMethod,
//First arg in args is instance!!
    val args: List<ArgumentDescriptor>,
    val argsChoosingStrategy: ChoosingStrategy<ArgumentDescriptor>,
    private val parent: Seed? = null,
    var coverage: List<JcInst>? = null,
    var argsInitialDescriptors: List<UTestValueDescriptor?>? = null,
    var argsResDescriptors: List<UTestValueDescriptor?>? = null,
    var accessedFields: List<JcField?>? = null
) : Selectable() {

    companion object {
        val fieldInfo = SeedFieldsInfo()
    }

    init {
        for ((i, arg) in args.withIndex()) {
            val argType = arg.type.type
            if (argType is JcClassType && fieldInfo.hasClassBeenParsed(argType.jcClass)) continue
            fieldInfo.addArgInfo(targetMethod, i, argType, 0.0, 0)
            val jcClass = (argType as? JcClassType)?.jcClass ?: continue
            val fieldsToHandle = ArrayDeque<JcField>()
            val processedFields = hashSetOf<JcField>()
            jcClass.allDeclaredFields.forEach { jcField -> fieldsToHandle.add(jcField) }
            val cp = jcClass.classpath
            while (fieldsToHandle.isNotEmpty()) {
                val fieldToAdd = fieldsToHandle.removeFirst()
                if (processedFields.contains(fieldToAdd)) continue
                processedFields.add(fieldToAdd)
                fieldInfo.addFieldInfo(targetMethod, fieldToAdd, 0.0, 0)
                if (fieldToAdd.type.isClass) {
                    fieldToAdd.type.toJcClassOrInterface(cp)?.let { jcFieldType ->
                        if (!fieldInfo.hasClassBeenParsed(jcFieldType)) {
                            jcFieldType.declaredFields.forEach { fieldsToHandle.add(it) }
                        }
                    }
                }
            }
        }
    }

    fun getArgForMutation() = argsChoosingStrategy.chooseBest(args, 0)

    fun getArgForMutation(condition: (ArgumentDescriptor) -> Boolean): ArgumentDescriptor? {
        val filteredArgs = args.filter(condition)
        return if (filteredArgs.isEmpty()) null
        else argsChoosingStrategy.chooseBest(filteredArgs, 0)
    }

    fun getFieldForMutation() = fieldInfo.getBestField()
    fun getFieldForMutation(condition: (JcField) -> Boolean) =
        fieldInfo.getBestField(condition)

    fun addSeedExecutionInfo(execResult: UTestExecutionResult) = with(execResult) {
//        val score =
//            when (this) {
//                is UTestExecutionExceptionResult -> trace?.size ?: 0
//                is UTestExecutionFailedResult -> 0
//                is UTestExecutionInitFailedResult -> 0
//                is UTestExecutionSuccessResult -> trace?.size ?: 0
//                is UTestExecutionTimedOutResult -> 0
//            }
//        val newAverageScore = ((averageScore * numberOfChooses) + score) / (numberOfChooses + 1)
//        averageScore = newAverageScore
//        numberOfChooses += 1

        when (this) {
            is UTestExecutionExceptionResult -> {
                coverage = trace
                argsInitialDescriptors = listOf(initialState.instanceDescriptor) + initialState.argsDescriptors
                argsResDescriptors = listOf(resultState.instanceDescriptor) + resultState.argsDescriptors
                accessedFields = resultState.accessedFields
            }

            is UTestExecutionFailedResult -> {}
            is UTestExecutionInitFailedResult -> {
                coverage = trace
            }

            is UTestExecutionSuccessResult -> {
                coverage = trace
                argsInitialDescriptors = listOf(initialState.instanceDescriptor) + initialState.argsDescriptors
                argsResDescriptors = listOf(resultState.instanceDescriptor) + resultState.argsDescriptors
                accessedFields = resultState.accessedFields
            }

            is UTestExecutionTimedOutResult -> {}
        }
    }

    fun mutate(
        replace: ArgumentDescriptor,
        replacement: ArgumentDescriptor
    ): Seed {
        return Seed(
            targetMethod = targetMethod,
            args = args.map { if (it == replace) replacement else it },
            argsChoosingStrategy = argsChoosingStrategy,
            parent = this,
            coverage = null,
            argsInitialDescriptors = null,
            argsResDescriptors = null,
            accessedFields = null
        )
    }

    fun toUTest(): UTest {
        val allInitStatements = args.flatMap { it.initialExprs }
        val callStatement =
            if (targetMethod.isStatic) {
                UTestStaticMethodCall(targetMethod, args.map { it.instance })
            } else {
                val instance = args.first().instance
                val args =
                    if (args.size == 1) {
                        listOf()
                    } else {
                        args.drop(1).map { it.instance }
                    }
                UTestMethodCall(instance, targetMethod, args)
            }
        return UTest(allInitStatements, callStatement)
    }

    fun getFieldsInTermsOfUTest(jcTargetField: JcField): List<Pair<ArgumentDescriptor, List<UTestGetFieldExpression>>> {
        val res = ArrayList<Pair<ArgumentDescriptor, List<UTestGetFieldExpression>>>()
        for ((ind, arg) in args.withIndex()) {
            val argDescriptor = argsInitialDescriptors?.getOrNull(ind) ?: continue
            val pathsToField = argDescriptor.getPossiblePathsToField(jcTargetField)
            val tmpRes = ArrayList<UTestGetFieldExpression>()
            var curInstance = arg.instance
            for (possiblePath in pathsToField) {
                for (jcField in possiblePath) {
                    val newInstance = UTestGetFieldExpression(curInstance, jcField)
                    tmpRes.add(newInstance)
                    curInstance = newInstance
                }
                res.add(arg to tmpRes.toList())
                tmpRes.clear()
            }
        }
        return res
    }

    class ArgumentDescriptor(
        val instance: UTestExpression,
        val type: JcTypeWrapper,
        val initialExprs: List<UTestInst>
    ): Selectable()

}


