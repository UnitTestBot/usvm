package org.usvm.annotations.codegeneration

import org.usvm.annotations.ids.SlotId

fun generateAvailableSlotInitialization(): String {
    val size = SlotId.entries.size
    val prefix = """
        AVAILABLE_SLOTS = PyMem_RawMalloc(sizeof(PyType_Slot) * ${size + 1});
    """.trimIndent()
    val items = SlotId.entries.map {
        requireNotNull(it.slotName)
        "AVAILABLE_SLOTS[${it.getMaskBit()}] = Virtual_${it.slotName};".trimIndent()
    }
    val postfix = "AVAILABLE_SLOTS[$size] = final_slot;"
    return "#define AVAILABLE_SLOT_INITIALIZATION \\\n" +
        prefix.replace("\n", "\\\n") + "\\\n" +
        items.joinToString("\n").replace("\n", "\\\n") + "\\\n" +
        postfix.replace("\n", "\\\n") + "\n"
}
