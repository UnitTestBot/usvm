package org.usvm.machine.interpreter.transformers

import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcInstExtFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcAddExpr
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcGeExpr
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcInstLocation
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.int
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext

object JcMultiDimArrayAllocationTransformer : JcInstExtFeature {
    override fun transformInstList(method: JcMethod, list: JcInstList<JcInst>): JcInstList<JcInst> {
        val multiDimArrayAllocations = list.mapNotNull { inst ->
            val assignInst = inst as? JcAssignInst ?: return@mapNotNull null
            val arrayAllocation = assignInst.rhv as? JcNewArrayExpr ?: return@mapNotNull null
            if (arrayAllocation.dimensions.size == 1) return@mapNotNull null
            assignInst to arrayAllocation
        }

        if (multiDimArrayAllocations.isEmpty()) return list

        val transformer = JcSingleInstructionTransformer(list)
        for ((assignInst, arrayAllocation) in multiDimArrayAllocations) {
            transformer.generateReplacementBlock(assignInst) {
                generateBlock(
                    method.enclosingClass.classpath,
                    assignInst.lhv, arrayAllocation
                )
            }
        }

        return transformer.buildInstList()
    }

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
    private fun BlockGenerationContext.generateBlock(
        cp: JcClasspath,
        resultVariable: JcValue,
        arrayAllocation: JcNewArrayExpr
    ) {
        val type = arrayAllocation.type as? JcArrayType
            ?: error("Incorrect array allocation: $arrayAllocation")

        val arrayVar = generateBlock(cp, type, arrayAllocation.dimensions, dimensionIdx = 0)
        addInstruction { loc ->
            JcAssignInst(loc, resultVariable, arrayVar)
        }
    }

    private fun BlockGenerationContext.generateBlock(
        cp: JcClasspath,
        type: JcArrayType,
        dimensions: List<JcValue>,
        dimensionIdx: Int
    ): JcValue {
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

        val nestedArrayVar = generateBlock(cp, nestedArrayType, dimensions, dimensionIdx + 1)

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

        replaceInstructionAtLocation(initStartLoc) { blockStartInst ->
            val blockEnd = JcInstRef(initEndLoc.index + 1)
            replaceEndLabelStub(blockStartInst as JcIfInst, blockEnd)
        }

        return arrayVar
    }

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
