package org.usvm.fuzzer.seed

import io.leangen.geantyref.GenericTypeReflector
import org.jacodb.api.*
import org.jacodb.api.cfg.JcInst
import org.jacodb.impl.cfg.util.isClass
import org.usvm.fuzzer.api.*
import org.usvm.fuzzer.position.SeedFieldsInfo
import org.usvm.fuzzer.position.SeedMethodsInfo
import org.usvm.fuzzer.strategy.ChoosingStrategy
import org.usvm.fuzzer.strategy.FairStrategy
import org.usvm.fuzzer.strategy.Selectable
import org.usvm.fuzzer.types.JcTypeWrapper
import org.usvm.fuzzer.util.UTestChildrenCollectVisitor
import org.usvm.fuzzer.util.createJcTypeWrapper
import org.usvm.fuzzer.util.findGetter
import org.usvm.fuzzer.util.getPossiblePathsToField
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.UTestValueDescriptor
import org.usvm.instrumentation.util.*
import kotlin.collections.ArrayDeque

data class Seed(
    val targetMethod: JcMethod,
//First arg in args is instance!!
    val args: List<ArgumentDescriptor>,
    val argsChoosingStrategy: ChoosingStrategy<ArgumentDescriptor>,
    val parent: Seed? = null,
    var coverage: Map<JcInst, Long>? = null,
    var argsInitialDescriptors: List<UTestValueDescriptor?>? = null,
    var argsResDescriptors: List<UTestValueDescriptor?>? = null,
    var accessedFields: List<JcField?>? = null,
    var executionResultType: Class<*>? = null
) : Selectable() {

    companion object {
        val fieldInfo = SeedFieldsInfo(FairStrategy())
        val methodInfo = SeedMethodsInfo(FairStrategy())
    }

    init {
        for ((i, arg) in args.withIndex()) {
            val argType = arg.type.type
            if (argType is JcClassType && fieldInfo.hasClassBeenParsed(argType.jcClass)) continue
            fieldInfo.addArgInfo(targetMethod, i, argType, 0.0, 0)
            val jcClass = (argType as? JcClassType)?.jcClass ?: continue

            //Add method info
            val allMethods =
                (jcClass.allDeclaredMethods + jcClass.innerClasses.flatMap { it.allDeclaredMethods }).toSet().toList()
            allMethods.forEach { jcMethod ->
                methodInfo.addMethodInfo(targetMethod, jcMethod, 0.0, 0)
            }

            val fieldsToHandle = ArrayDeque<JcField>()
            val processedFields = hashSetOf<JcField>()
            val allFields = jcClass.allDeclaredFields + jcClass.innerClasses.flatMap { it.allDeclaredFields }
            allFields.forEach { jcField -> fieldsToHandle.add(jcField) }
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

    //    fun getUTestForMutation(
//        classLoader: ClassLoader,
//        cp: JcClasspath,
//        condition: (SeedFieldsInfo.FieldInfo) -> Boolean,
//    ): ArgumentFieldDescriptor? {
//        val fieldForMutation = fieldInfo.getBestField(condition)!!
//        return if (fieldForMutation.isArg()) {
//            val argPosition = fieldForMutation.getArgPosition()
//            val actualArg = args[argPosition]
//            ArgumentFieldDescriptor(
//                parentArgument = actualArg,
//                instance = actualArg.instance,
//                actualType = actualArg.type,
//                isActualArg = true
//            )
//        } else {
//            getFieldsInTermsOfUTest(fieldForMutation.jcField, classLoader, cp)
//        }
//    }
//
//
    fun getArgForMutation() = argsChoosingStrategy.chooseBest(args, 0)

    fun getArgForMutation(condition: (ArgumentDescriptor) -> Boolean): ArgumentDescriptor? {
        val filteredArgs = args.filter(condition)
        return if (filteredArgs.isEmpty()) null
        else argsChoosingStrategy.chooseBest(filteredArgs, 0)
    }
//
//    fun getFieldForMutation() = fieldInfo.getBestField()
//    fun getFieldForMutation(condition: (JcField) -> Boolean) =
//        fieldInfo.getBestField(condition)

    fun addSeedExecutionInfo(execResult: UTestExecutionResult) = with(execResult) {
        when (this) {
            is UTestExecutionExceptionResult -> {
                coverage = trace
                argsInitialDescriptors = listOf(initialState.instanceDescriptor) + initialState.argsDescriptors
                argsResDescriptors = listOf(resultState.instanceDescriptor) + resultState.argsDescriptors
                accessedFields = resultState.accessedFields + resultState.statics.keys
                resultState.statics.keys.forEach { staticJcField ->
                    fieldInfo.addFieldInfo(targetMethod, staticJcField, 0.0, 0)
                }
            }

            is UTestExecutionFailedResult -> {}
            is UTestExecutionInitFailedResult -> {
                coverage = trace
            }

            is UTestExecutionSuccessResult -> {
                coverage = trace
                argsInitialDescriptors = listOf(initialState.instanceDescriptor) + initialState.argsDescriptors
                argsResDescriptors = listOf(resultState.instanceDescriptor) + resultState.argsDescriptors
                accessedFields = resultState.accessedFields + resultState.statics.keys
                resultState.statics.keys.forEach { staticJcField ->
                    fieldInfo.addFieldInfo(targetMethod, staticJcField, 0.0, 0)
                }
            }

            is UTestExecutionTimedOutResult -> {}
        }
        executionResultType = execResult::class.java
    }

    fun mutate(
        replace: ArgumentDescriptor,
        replacement: ArgumentDescriptor
    ): Seed {
        return Seed(
            targetMethod = targetMethod,
            args = args.map { if (it == replace) replacement else it },
            argsChoosingStrategy = argsChoosingStrategy,
            parent = null,
            coverage = null,
            argsInitialDescriptors = null,
            argsResDescriptors = null,
            accessedFields = null
        )
    }

    fun mutate(newArgs: List<ArgumentDescriptor>): Seed {
        return Seed(
            targetMethod = targetMethod,
            args = newArgs,
            argsChoosingStrategy = argsChoosingStrategy,
            parent = null,
            coverage = null,
            argsInitialDescriptors = null,
            argsResDescriptors = null,
            accessedFields = null
        )
    }

    fun replace(
        argument: ArgumentDescriptor,
        replacedInst: UTypedTestInst,
        replacement: UTypedTestInst,
        replacementInitialExpr: List<UTypedTestInst>,
    ): ArgumentDescriptor {
        if (replacedInst == argument.instance) {
            return ArgumentDescriptor(
                replacement as UTypedTestExpression,
                argument.type,
                replacementInitialExpr
            )
        }
        val argInitialExpressions = argument.initialExprs
        val instructionToRemove = mutableSetOf(replacedInst)
        var newSize = -1
        var oldSize = instructionToRemove.size
        while (oldSize != newSize) {
            oldSize = newSize
            argInitialExpressions.reversed().forEach {
                it.accept(UTypedTestInstsSimplifier(instructionToRemove))
            }
            newSize = instructionToRemove.size
        }
        val newInitInstructions = argInitialExpressions.filter { it !in instructionToRemove }
        val remappedInstructions = mutableMapOf(replacedInst to replacement)
        val remapped = newInitInstructions.map {
            it.accept(UTypedTestInstRemapper(remappedInstructions))
        }
        return ArgumentDescriptor(
            argument.instance,
            argument.type,
            remapped + replacementInitialExpr + replacement
        )
    }

//    fun replace(
//        argument: ArgumentDescriptor,
//        mutatedUTestInst: UTestInst?,
//        replacedUTest: UTestInst?,
//        replacement: UTestInst,
//        replacementInitialExpr: List<UTestInst>
//    ): ArgumentDescriptor {
//        if (mutatedUTestInst == argument.instance) {
//            return ArgumentDescriptor(
//                replacement as UTestExpression,
//                argument.type,
//                replacementInitialExpr
//            )
//        } else {
//            val removedInst = Collections.newSetFromMap(IdentityHashMap<UTestInst, Boolean>())
//            removedInst.add(replacedUTest)
//            val allInstructions =
//                argument.initialExprs.map { it to it.accept(UTestChildrenCollectVisitor()) }.toMutableList()
//            while (true) {
//                val prevSize = allInstructions.size
//                val allInstructionsIterator = allInstructions.listIterator()
//                while (allInstructionsIterator.hasNext()) {
//                    val (parentInst, instructions) = allInstructionsIterator.next()
//                    if (parentInst == mutatedUTestInst) {
//                        allInstructionsIterator.remove()
//                    } else if (instructions.any { it in removedInst }) {
//                        removedInst.addAll(instructions)
//                        allInstructionsIterator.remove()
//                    }
//                }
//                val newSize = allInstructions.size
//                if (prevSize == newSize || newSize == 0) break
//            }
//            return ArgumentDescriptor(
//                argument.instance,
//                argument.type,
//                allInstructions.map { it.first } + replacement + replacementInitialExpr
//            )
//        }
//    }

    fun toUTest(): UTest {
        val converter = UTypedTest2UTestConverter()
        val allInitStatements = args.flatMap { it.initialExprs }.map { it.accept(converter) }
        val callStatement =
            if (targetMethod.isStatic) {
                UTestStaticMethodCall(targetMethod, args.map { it.instance.accept(converter) as UTestExpression })
            } else {
                val instance = args.first().instance.accept(converter) as UTestExpression
                val args =
                    if (args.size == 1) {
                        listOf()
                    } else {
                        args.drop(1).map { it.instance.accept(converter) as UTestExpression }
                    }
                UTestMethodCall(instance, targetMethod, args)
            }
        return UTest(allInitStatements, callStatement)
    }

    fun getFieldInTermsOfUTest(
        jcTargetField: JcField,
        userClassLoader: ClassLoader,
        cp: JcClasspath
    ): ArgumentFieldDescriptor? {
        for ((ind, arg) in args.withIndex()) {
            val argDescriptor = argsInitialDescriptors?.getOrNull(ind) ?: continue
            val pathToField = argDescriptor.getPossiblePathsToField(jcTargetField).randomOrNull() ?: continue
            var curInstance = arg.instance
            var curType = arg.type.actualJavaType
            for (jcField in pathToField) {
                val newInstance =
                    if (jcField.isPublic || jcField.isPackagePrivate) {
                        UTypedTestGetFieldExpression(curInstance, jcField, curType.createJcTypeWrapper(cp))
                    } else {
                        val getter = jcField.findGetter() ?: return null
                        UTypedTestMethodCall(curInstance, getter, listOf())
                    }
                val jField = jcField.toJavaField(userClassLoader) ?: return null
                curType = GenericTypeReflector.getFieldType(jField, curType)
                curInstance = newInstance
            }
            return ArgumentFieldDescriptor(
                parentArgument = arg,
                instance = curInstance,
                actualType = curType.createJcTypeWrapper(cp),
                isActualArg = false
            )
        }
        return null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Seed

        if (targetMethod != other.targetMethod) return false
        if (args != other.args) return false

        return true
    }

    override fun hashCode(): Int {
        var result = targetMethod.hashCode()
        result = 31 * result + args.hashCode()
        return result
    }


    class ArgumentDescriptor(
        val instance: UTypedTestExpression,
        val type: JcTypeWrapper,
        val initialExprs: List<UTypedTestInst>
    ) : Selectable() {
        fun getChildrenExpressions(parent: UTestInst) =
            parent.accept(UTestChildrenCollectVisitor()).filter { it == parent }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ArgumentDescriptor

            if (instance != other.instance) return false
            if (type != other.type) return false
            if (initialExprs != other.initialExprs) return false

            return true
        }

        override fun hashCode(): Int {
            var result = instance.hashCode()
            result = 31 * result + type.hashCode()
            result = 31 * result + initialExprs.hashCode()
            return result
        }


    }

    class ArgumentFieldDescriptor(
        val parentArgument: ArgumentDescriptor,
        val instance: UTypedTestExpression,
        val actualType: JcTypeWrapper,
        val isActualArg: Boolean
    )

}


