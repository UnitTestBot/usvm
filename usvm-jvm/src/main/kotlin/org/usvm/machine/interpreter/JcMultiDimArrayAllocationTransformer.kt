package org.usvm.machine.interpreter

import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcInstExtFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcAddExpr
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCatchInst
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcExprVisitor
import org.jacodb.api.jvm.cfg.JcGeExpr
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcInstLocation
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.int
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.cfg.JcInstLocationImpl
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

object JcMultiDimArrayAllocationTransformer : JcInstExtFeature {
    override fun transformInstList(method: JcMethod, list: JcInstList<JcInst>): JcInstList<JcInst> {
        val multiDimArrayAllocations = list.mapNotNull { inst ->
            val assignInst = inst as? JcAssignInst ?: return@mapNotNull null
            val arrayAllocation = assignInst.rhv as? JcNewArrayExpr ?: return@mapNotNull null
            if (arrayAllocation.dimensions.size == 1) return@mapNotNull null
            assignInst to arrayAllocation
        }

        if (multiDimArrayAllocations.isEmpty()) return list

        val modifiedInstructions = list.instructions.toMutableList()
        val maxLocalVarIndex = modifiedInstructions.maxOfOrNull { LocalVarMaxIndexFinder.find(it.operands) } ?: -1

        var generatedLocalVarIndex = maxLocalVarIndex + 1
        val modifiedLocationIndices = hashMapOf<Int, List<Int>>()

        for ((assignInst, arrayAllocation) in multiDimArrayAllocations) {
            val originalLocation = assignInst.location
            val blockGenerator = ArrayInitializationBlockGenerator(
                method.enclosingClass.classpath,
                originalLocation, modifiedInstructions, generatedLocalVarIndex
            )

            blockGenerator.generateBlock(assignInst.lhv, arrayAllocation)
            blockGenerator.generateBlockJump()

            generatedLocalVarIndex = blockGenerator.localVarIndex
            val generatedLocations = blockGenerator.generatedLocations
            modifiedLocationIndices[originalLocation.index] = generatedLocations.map { it.index }
        }

        fixCatchBlockThrowers(modifiedInstructions, modifiedLocationIndices)

        return JcInstListImpl(modifiedInstructions)
    }

    /**
     * Since we generate multiple instructions instead of a single one,
     * we must ensure that all catchers of the original instruction will
     * catch exceptions of generated instructions.
     * */
    private fun fixCatchBlockThrowers(
        instructions: MutableList<JcInst>,
        modifiedLocationIndices: Map<Int, List<Int>>
    ) {
        for (i in instructions.indices) {
            val instruction = instructions[i]
            if (instruction !is JcCatchInst) continue

            val throwers = instruction.throwers.toMutableList()
            for (throwerIdx in throwers.indices) {
                val thrower = throwers[throwerIdx]
                val generatedLocations = modifiedLocationIndices[thrower.index] ?: continue
                generatedLocations.mapTo(throwers) { JcInstRef(it) }
            }

            instructions[i] = with(instruction) {
                JcCatchInst(location, throwable, throwableTypes, throwers)
            }
        }
    }

    private class ArrayInitializationBlockGenerator(
        private val cp: JcClasspath,
        private val originalLocation: JcInstLocation,
        private val instructions: MutableList<JcInst>,
        initialLocalVarIndex: Int,
    ) {
        var localVarIndex: Int = initialLocalVarIndex
        val generatedLocations = mutableListOf<JcInstLocation>()

        fun nextLocalVar(name: String, type: JcType) = JcLocalVar(localVarIndex++, name, type)

        /**
         * original:
         * result = new T[d0][d1][d2]
         *
         * rewrited:
         * a0: T[][][] = new T[d0][][]
         * i0 = 0
         * INIT_0_START:
         *   if (i0 >= d0) goto INIT_0_END
         *
         *   a1: T[][] = new T[d1][]
         *   i1 = 0
         *
         *   INIT_1_START:
         *      if (i1 >= d1) goto INIT_1_END
         *
         *      a2: T[] = new T[d2]
         *
         *      a1[i1] = a2
         *      i1++
         *      goto INIT_1_START
         *
         *   INIT_1_END:
         *      a0[i0] = a1
         *      i0++
         *      goto INIT_0_START
         *
         * INIT_0_END:
         *   result = a0
         * */
        fun generateBlock(resultVariable: JcValue, arrayAllocation: JcNewArrayExpr) {
            val type = arrayAllocation.type as? JcArrayType
                ?: error("Incorrect array allocation: $arrayAllocation")

            val arrayVar = generateBlock(type, arrayAllocation.dimensions, dimensionIdx = 0)
            addInstruction { loc ->
                JcAssignInst(loc, resultVariable, arrayVar)
            }
        }

        private fun generateBlock(type: JcArrayType, dimensions: List<JcValue>, dimensionIdx: Int): JcValue {
            val dimension = dimensions[dimensionIdx]
            val arrayVar = nextLocalVar("a_${originalLocation.index}_$dimensionIdx", type)

            addInstruction { loc ->
                JcAssignInst(loc, arrayVar, JcNewArrayExpr(type, listOf(dimension)))
            }

            if (dimensionIdx == dimensions.lastIndex) return arrayVar

            val initializerIdxVar = nextLocalVar("i_${originalLocation.index}_$dimensionIdx", cp.int)
            addInstruction { loc ->
                JcAssignInst(loc, initializerIdxVar, JcInt(0, cp.int))
            }

            val initStartLoc: JcInstLocation
            addInstruction { loc ->
                initStartLoc = loc

                val cond = JcGeExpr(cp.boolean, initializerIdxVar, dimension)
                val nextInst = JcInstRef(loc.index + 1)
                JcIfInst(loc, cond, END_LABEL_STUB, nextInst)
            }

            val nestedArrayType = type.elementType as? JcArrayType
                ?: error("Incorrect array type: $type")

            val nestedArrayVar = generateBlock(nestedArrayType, dimensions, dimensionIdx + 1)

            addInstruction { loc ->
                val arrayElement = JcArrayAccess(arrayVar, initializerIdxVar, nestedArrayType)
                JcAssignInst(loc, arrayElement, nestedArrayVar)
            }

            addInstruction { loc ->
                JcAssignInst(loc, initializerIdxVar, JcAddExpr(cp.int, initializerIdxVar, JcInt(1, cp.int)))
            }

            val initEndLoc: JcInstLocation
            addInstruction { loc ->
                initEndLoc = loc
                JcGotoInst(loc, JcInstRef(initStartLoc.index))
            }

            val blockStartInst = instructions[initStartLoc.index] as JcIfInst
            val blockEnd = JcInstRef(initEndLoc.index + 1)
            instructions[initStartLoc.index] = replaceEndLabelStub(blockStartInst, blockEnd)

            return arrayVar
        }

        fun generateBlockJump() {
            addInstruction { loc ->
                JcGotoInst(loc, JcInstRef(originalLocation.index + 1))
            }

            val arrayInitializationBlockStart = JcInstRef(generatedLocations.first().index)
            instructions[originalLocation.index] = JcGotoInst(originalLocation, arrayInitializationBlockStart)
        }

        @OptIn(ExperimentalContracts::class)
        private inline fun addInstruction(body: (JcInstLocation) -> JcInst) {
            contract {
                callsInPlace(body, InvocationKind.EXACTLY_ONCE)
            }

            instructions.addInstruction(originalLocation) { loc ->
                generatedLocations += loc
                body(loc)
            }
        }

        companion object {
            private val END_LABEL_STUB = JcInstRef(-1)

            private fun replaceEndLabelStub(inst: JcIfInst, replacement: JcInstRef): JcIfInst = with(inst) {
                JcIfInst(
                    location,
                    condition,
                    if (trueBranch == END_LABEL_STUB) replacement else trueBranch,
                    if (falseBranch == END_LABEL_STUB) replacement else falseBranch,
                )
            }
        }
    }

    private inline fun MutableList<JcInst>.addInstruction(origin: JcInstLocation, body: (JcInstLocation) -> JcInst) {
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
