package org.usvm.machine.interpreter.transformers

import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcCatchInst
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcExprVisitor
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcInstLocation
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.cfg.JcInstLocationImpl
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class JcSingleInstructionTransformer(originalInstructions: JcInstList<JcInst>) {
    val mutableInstructions = originalInstructions.instructions.toMutableList()
    private val maxLocalVarIndex = mutableInstructions.maxOfOrNull { LocalVarMaxIndexFinder.find(it.operands) } ?: -1

    var generatedLocalVarIndex = maxLocalVarIndex + 1
    val modifiedLocationIndices = hashMapOf<Int, List<Int>>()

    inline fun generateReplacementBlock(original: JcInst, blockGen: BlockGenerationContext.() -> Unit) {
        val originalLocation = original.location
        val ctx = BlockGenerationContext(originalLocation, generatedLocalVarIndex)

        ctx.blockGen()

        // add back jump from generated block
        ctx.addInstruction { loc ->
            JcGotoInst(loc, JcInstRef(originalLocation.index + 1))
        }

        // replace original instruction with jump to the generated block
        val replacementBlockStart = JcInstRef(ctx.generatedLocations.first().index)
        mutableInstructions[originalLocation.index] = JcGotoInst(originalLocation, replacementBlockStart)

        generatedLocalVarIndex = ctx.localVarIndex

        val generatedLocations = ctx.generatedLocations
        modifiedLocationIndices[originalLocation.index] = generatedLocations.map { it.index }
    }

    fun buildInstList(): JcInstList<JcInst> {
        fixCatchBlockThrowers()
        return JcInstListImpl(mutableInstructions)
    }

    /**
     * Since we generate multiple instructions instead of a single one,
     * we must ensure that all catchers of the original instruction will
     * catch exceptions of generated instructions.
     * */
    private fun fixCatchBlockThrowers() {
        for (i in mutableInstructions.indices) {
            val instruction = mutableInstructions[i]
            if (instruction !is JcCatchInst) continue

            val throwers = instruction.throwers.toMutableList()
            for (throwerIdx in throwers.indices) {
                val thrower = throwers[throwerIdx]
                val generatedLocations = modifiedLocationIndices[thrower.index] ?: continue
                generatedLocations.mapTo(throwers) { JcInstRef(it) }
            }

            mutableInstructions[i] = with(instruction) {
                JcCatchInst(location, throwable, throwableTypes, throwers)
            }
        }
    }

    inner class BlockGenerationContext(
        val originalLocation: JcInstLocation,
        initialLocalVarIndex: Int,
    ) {
        var localVarIndex: Int = initialLocalVarIndex
        val generatedLocations = mutableListOf<JcInstLocation>()

        fun nextLocalVar(name: String, type: JcType) = JcLocalVar(localVarIndex++, name, type)

        @OptIn(ExperimentalContracts::class)
        inline fun addInstruction(body: (JcInstLocation) -> JcInst) {
            contract {
                callsInPlace(body, InvocationKind.EXACTLY_ONCE)
            }

            mutableInstructions.addInstruction(originalLocation) { loc ->
                generatedLocations += loc
                body(loc)
            }
        }

        fun replaceInstructionAtLocation(loc: JcInstLocation, replacement: (JcInst) -> JcInst) {
            val currentInst = mutableInstructions[loc.index]
            mutableInstructions[loc.index] = replacement(currentInst)
        }
    }

    @OptIn(ExperimentalContracts::class)
    inline fun MutableList<JcInst>.addInstruction(origin: JcInstLocation, body: (JcInstLocation) -> JcInst) {
        contract {
            callsInPlace(body, InvocationKind.EXACTLY_ONCE)
        }

        val index = size
        val newLocation = JcInstLocationImpl(origin.method, index, origin.lineNumber)
        val instruction = body(newLocation)
        check(size == index)
        add(instruction)
    }

    private object LocalVarMaxIndexFinder : JcExprVisitor.Default<Int> {
        override fun defaultVisitJcExpr(expr: JcExpr) = find(expr.operands)
        override fun visitJcLocalVar(value: JcLocalVar) = value.index
        fun find(expressions: Iterable<JcExpr>): Int = expressions.maxOfOrNull { it.accept(this) } ?: -1
    }
}
