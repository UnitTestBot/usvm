package org.usvm.annotations.codegeneration

import org.usvm.annotations.ids.SlotId

fun generateAvailableSlotInitialization(): String {
    val filtered = SlotId.entries.filter { !it.mandatory }
    val size = filtered.size
    val prefix = """
        AVAILABLE_SLOTS = PyMem_RawMalloc(sizeof(PyType_Slot) * ${size + 1});
    """.trimIndent()
    val items = filtered.map {
        requireNotNull(it.slotName)
        "AVAILABLE_SLOTS[${it.getMaskBit()}] = Virtual_${it.slotName};".trimIndent()
    }
    val postfix = "AVAILABLE_SLOTS[$size] = final_slot;"
    return "#define AVAILABLE_SLOT_INITIALIZATION \\\n" +
        prefix.replace("\n", "\\\n") + "\\\n" +
        items.joinToString("\n").replace("\n", "\\\n") + "\\\n" +
        postfix.replace("\n", "\\\n") + "\n"
}

fun generateMandatorySlotMacro(): String {
    val filtered = SlotId.values().filter { it.mandatory }
    val size = filtered.size
    val numberMacro = "#define MANDATORY_SLOTS_NUMBER $size".trimIndent()
    val prefix = "#define INCLUDE_MANDATORY_SLOTS".trimIndent()
    if (size == 0) {
        return numberMacro + "\n\n" + prefix + "\n"
    }
    val items = filtered.map {
        requireNotNull(it.slotName)
        "slots[i++] = Virtual_${it.slotName};".trimIndent()
    }
    return numberMacro + "\n\n" +
        prefix + " \\\n" +
        items.joinToString("\n").replace("\n", "\\\n") + "\n"
}
