package org.usvm.annotations.ids

/*
 * tp_hash cannot be disabled, however
 * if you don't explicitly specify that the type HAS that slot,
 * you will not be able to disable the tp_richcompare slot
 *
 * For that reason, some slots are marked as mandatory.
 * They cannot be disabled using mask, so they do not have
 * a mask bit number.
 *
 * The usage of swapSlotBit or setSlotBit with these slots
 * will not have any effect on the mask.
 */
enum class SlotId(
    val slotName: String,
    val mandatory: Boolean = false,
) {
    TpGetattro("tp_getattro", true),
    TpSetattro("tp_setattro", true),
    TpRichcompare("tp_richcompare"),
    TpIter("tp_iter"),
    TpHash("tp_hash", true),
    TpCall("tp_call"),
    TpDealloc("tp_dealloc", true),
    NbBool("nb_bool"),
    NbAdd("nb_add"),
    NbSubtract("nb_subtract"),
    NbMultiply("nb_multiply"),
    NbMatrixMultiply("nb_matrix_multiply"),
    NbNegative("nb_negative"),
    NbPositive("nb_positive"),
    SqLength("sq_length"),
    MpSubscript("mp_subscript"),
    MpAssSubscript("mp_ass_subscript"),
    SqConcat("sq_concat"),
    ;
    fun getMaskBit(): Int = ordinal
}

fun ByteArray.setSlotBit(slot: SlotId, state: Boolean): ByteArray {
    if (slot.mandatory) return this
    val bitPosition = this.size * Byte.SIZE_BITS - 1 - slot.getMaskBit()
    val byteIndex = bitPosition / Byte.SIZE_BITS
    val bitMask = 1 shl (slot.getMaskBit() % Byte.SIZE_BITS)
    if (state) {
        this[byteIndex] = (this[byteIndex].toInt() or bitMask).toByte()
    } else {
        this[byteIndex] = (this[byteIndex].toInt() and (bitMask.inv())).toByte()
    }
    return this // just to allow Builder-like usage
}
