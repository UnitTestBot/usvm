package org.usvm.fuzzer.seed

import org.jacodb.api.JcClassType
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.impl.cfg.util.isClass
import org.jacodb.impl.features.classpaths.virtual.JcVirtualFieldImpl
import org.objectweb.asm.Opcodes
import org.usvm.fuzzer.position.PositionTrie
import org.usvm.fuzzer.strategy.ChoosingStrategy
import org.usvm.fuzzer.strategy.RandomStrategy
import org.usvm.fuzzer.strategy.Selectable
import org.usvm.instrumentation.testcase.UTest
import org.usvm.instrumentation.testcase.api.UTestExpression
import org.usvm.instrumentation.testcase.api.UTestMethodCall
import org.usvm.instrumentation.testcase.api.UTestStaticMethodCall
import org.usvm.instrumentation.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.full.companionObject

data class Seed(
    val targetMethod: JcMethod,
//First arg in args is instance!!
    val args: List<Descriptor>,
    private val parent: Seed? = null
) : Selectable() {


    companion object {
        lateinit var treesForArgs: List<PositionTrie>

        fun initTrees(numberOfArgs: Int) {
            treesForArgs = List(numberOfArgs) { PositionTrie() }
        }

        fun isInitialized() = this::treesForArgs.isInitialized
    }

    val positions: ArrayList<Position> = arrayListOf()
    private var positionChoosingStrategy: ChoosingStrategy<Position> = RandomStrategy()
    private val curIndex = AtomicInteger(0)

    private fun setChoosingStrategy(newStrategy: ChoosingStrategy<Position>) {
        positionChoosingStrategy = newStrategy
    }

    private fun addPosition(field: JcField, descriptor: Descriptor) {
        positions.add(Position(curIndex.getAndIncrement(), 0.0, field, descriptor))
    }

    init {
        if (!isInitialized()) {
            initTrees(args.size)
        }
        for ((i, arg) in args.withIndex()) {
            val tree = treesForArgs[i]
            val jcClass =
                if (arg.type is JcClassType) {
                    arg.type.toJcClass() ?: error("Cant convert ${arg.type.typeName} to class")
                } else {
                    null
                }
            val type = arg.type
            tree.addRoot(type)
            val fieldsToHandle = ArrayDeque<List<JcField>>()
            val thisJcField = JcVirtualFieldImpl("this", Opcodes.ACC_PUBLIC, type.getTypename())
            fieldsToHandle.add(listOf(thisJcField))
            jcClass?.allDeclaredFields?.forEach { jcField -> fieldsToHandle.add(listOf(jcField)) }
            val cp = type.classpath
            while (fieldsToHandle.isNotEmpty()) {
                val fieldChain = fieldsToHandle.removeFirst()
                tree.addPosition(type, fieldChain)
                val addedField = fieldChain.last()
                if (addedField.type.isClass) {
                    addedField.type.toJcClassOrInterface(cp)?.allDeclaredFields?.forEach { field ->
                        fieldsToHandle.add(fieldChain + listOf(field))
                    }
                }
            }
        }
    }

    //fun spawn(): Seed = Seed(args, weight, positions, this)

    fun mutate(position: Int, expressionsToAdd: List<UTestExpression>): Seed {
        val descriptor = positions[position].descriptor
        val newDescriptor = Descriptor(descriptor.instance, descriptor.type, descriptor.initialExprs + expressionsToAdd)
        val newArgs = args.map { if (it == descriptor) newDescriptor else it }
        return Seed(targetMethod, newArgs, this)
    }

    fun mutate(position: Int, expr: UTestExpression) = mutate(position, listOf(expr))

    fun getPositionToMutate(iterationNumber: Int) = positionChoosingStrategy.chooseBest(positions, iterationNumber)

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

    class Descriptor(val instance: UTestExpression, val type: JcType, val initialExprs: List<UTestExpression>)

    data class Position(val index: Int, var score: Double, val field: JcField, val descriptor: Descriptor) :
        Selectable()

}


