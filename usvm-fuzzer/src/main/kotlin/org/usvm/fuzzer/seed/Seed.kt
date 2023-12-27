package org.usvm.fuzzer.seed

import org.jacodb.api.JcClassType
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.impl.cfg.util.isClass
import org.usvm.fuzzer.position.SeedFieldsInfo
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

    //fun spawn(): Seed = Seed(args, weight, positions, this)

    fun mutate(position: Int, expressionsToAdd: List<UTestInst>): Seed {
//        val descriptor = positions[position].descriptor
//        val newDescriptor = Descriptor(descriptor.instance, descriptor.type, descriptor.initialExprs + expressionsToAdd)
//        val newArgs = args.map { if (it == descriptor) newDescriptor else it }
//        return Seed(targetMethod, newArgs, this)
        return this.copy()
    }

    fun mutate(position: Int, expr: UTestInst) = mutate(position, listOf(expr))

    fun getPositionToMutate(iterationNumber: Int) {
        fieldInfo.getBestField().jcField
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
    )

    data class Position(val index: Int, var score: Double, val field: JcField, val argumentDescriptor: ArgumentDescriptor) :
        Selectable()

}


