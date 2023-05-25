package org.usvm.instrumentation.util

import org.objectweb.asm.Label
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode

internal class LabelFilterer(private val mn: MethodNode) {

    fun build(): MethodNode {
        val instructionList = mn.instructions
        val replacementsList = MutableList(instructionList.size()) { -1 }

        val new = MethodNode(mn.access, mn.name, mn.desc, mn.signature, mn.exceptions.toTypedArray())
        var prev: Int = -1
        for ((index, inst) in instructionList.withIndex()) {
            if (prev != -1 && inst is LabelNode) {
                var actualPrev = prev
                while (replacementsList[actualPrev] != -1)
                    actualPrev = replacementsList[actualPrev]
                replacementsList[index] = actualPrev
            }
            prev = if (inst is LabelNode) index else -1
        }

        val clonedLabelsList = instructionList.map { if (it is LabelNode) LabelNode(Label()) else null }
        val newReplacements = clonedLabelsList.mapIndexedNotNullTo(mutableMapOf()) { index, label ->
            if (label != null) {
                val first = instructionList[index] as LabelNode
                val second = when {
                    replacementsList[index] != -1 -> clonedLabelsList[replacementsList[index]]!!
                    else -> label
                }
                first to second
            } else null
        }

        for ((index, inst) in instructionList.withIndex()) {
            val newInst = when (inst) {
                is LabelNode -> when {
                    replacementsList[index] != -1 -> null
                    clonedLabelsList[index] != null -> clonedLabelsList[index]!!
                    else -> inst.clone(newReplacements)
                }

                else -> inst.clone(newReplacements)
            }
            if (newInst != null) new.instructions.add(newInst)
        }

        for (tryCatch in mn.tryCatchBlocks) {
            val tcb = TryCatchBlockNode(
                newReplacements.getValue(tryCatch.start), newReplacements.getValue(tryCatch.end),
                newReplacements.getValue(tryCatch.handler), tryCatch.type
            )
            tcb.visibleTypeAnnotations = tryCatch.visibleTypeAnnotations?.toList()
            tcb.invisibleTypeAnnotations = tryCatch.invisibleTypeAnnotations?.toList()
            new.tryCatchBlocks.add(tcb)
        }

        new.visibleParameterAnnotations = mn.visibleParameterAnnotations?.clone()
        new.invisibleParameterAnnotations = mn.invisibleParameterAnnotations?.clone()

        new.visibleAnnotableParameterCount = mn.visibleAnnotableParameterCount
        new.invisibleAnnotableParameterCount = mn.invisibleAnnotableParameterCount

        new.parameters = mn.parameters?.toList().orEmpty()

        new.maxStack = mn.maxStack
        new.maxLocals = mn.maxLocals

        return new
    }
}
