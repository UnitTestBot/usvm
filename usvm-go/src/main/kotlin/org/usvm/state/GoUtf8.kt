package org.usvm.state

import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.memory.readGoArrayIndex
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeSubExpr
import org.usvm.type.GoBasicTypes

private const val MAX_UTF8_WIDTH = 4
private const val BYTE_TO_RUNE_EXTENSION = 24
private const val THREE_BYTE_WIDTH = 3
private const val FOUR_BYTE_WIDTH = 4
private const val THREE_BYTE_MIN_LEAD = 0xE0
private const val SURROGATE_LEAD = 0xED
private const val FOUR_BYTE_MIN_LEAD = 0xF0
private const val MAX_UNICODE_LEAD = 0xF4
private const val REPLACEMENT_RUNE = 0xFFFD

/** Go range decodes one UTF-8 rune; each malformed byte produces RuneError with width one. */
internal fun GoState.decodeUtf8Rune(
    string: UHeapRef,
    index: UExpr<USizeSort>,
    length: UExpr<USizeSort>,
): Pair<UExpr<UBv32Sort>, UExpr<USizeSort>> = with(ctx) {
    val bytes = (0 until MAX_UTF8_WIDTH).map { offset ->
        val byte = memory.readGoArrayIndex(
            string,
            mkSizeAddExpr(index, mkSizeExpr(offset)),
            GoBasicTypes.STRING,
            bv8Sort,
        )
        mkBvZeroExtensionExpr(extensionSize = BYTE_TO_RUNE_EXTENSION, value = byte).asExpr(bv32Sort)
    }
    val first = bytes[0]
    val remaining = mkSizeSubExpr(length, index)
    fun between(value: UExpr<UBv32Sort>, low: Int, high: Int): UBoolExpr = mkAnd(
        mkBvUnsignedGreaterOrEqualExpr(value, mkBv(low)),
        mkBvUnsignedLessOrEqualExpr(value, mkBv(high)),
    )
    fun continuation(offset: Int): UBoolExpr = between(bytes[offset], low = 0x80, high = 0xBF)
    fun payload(offset: Int, mask: Int, shift: Int): UExpr<UBv32Sort> =
        mkBvShiftLeftExpr(mkBvAndExpr(bytes[offset], mkBv(mask)), mkBv(shift))

    val ascii = between(first, low = 0, high = 0x7F)
    val twoBytes = mkAnd(
        mkSizeGeExpr(remaining, mkSizeExpr(2)),
        between(first, low = 0xC2, high = 0xDF),
        continuation(offset = 1),
    )
    val validThreeByteSecond = mkAnd(
        continuation(offset = 1),
        mkOr(mkNot(mkEq(first, mkBv(THREE_BYTE_MIN_LEAD))), between(bytes[1], low = 0xA0, high = 0xBF)),
        mkOr(mkNot(mkEq(first, mkBv(SURROGATE_LEAD))), between(bytes[1], low = 0x80, high = 0x9F)),
    )
    val threeBytes = mkAnd(
        mkSizeGeExpr(remaining, mkSizeExpr(THREE_BYTE_WIDTH)),
        between(first, low = 0xE0, high = 0xEF),
        validThreeByteSecond,
        continuation(offset = 2),
    )
    val validFourByteSecond = mkAnd(
        continuation(offset = 1),
        mkOr(mkNot(mkEq(first, mkBv(FOUR_BYTE_MIN_LEAD))), between(bytes[1], low = 0x90, high = 0xBF)),
        mkOr(mkNot(mkEq(first, mkBv(MAX_UNICODE_LEAD))), between(bytes[1], low = 0x80, high = 0x8F)),
    )
    val fourBytes = mkAnd(
        mkSizeGeExpr(remaining, mkSizeExpr(FOUR_BYTE_WIDTH)),
        between(first, low = 0xF0, high = 0xF4),
        validFourByteSecond,
        continuation(offset = 2),
        continuation(offset = 3),
    )

    val rune2 = mkBvOrExpr(payload(offset = 0, mask = 0x1F, shift = 6), payload(offset = 1, mask = 0x3F, shift = 0))
    val rune3 = mkBvOrExpr(
        payload(offset = 0, mask = 0x0F, shift = 12),
        mkBvOrExpr(payload(offset = 1, mask = 0x3F, shift = 6), payload(offset = 2, mask = 0x3F, shift = 0)),
    )
    val rune4 = mkBvOrExpr(
        payload(offset = 0, mask = 0x07, shift = 18),
        mkBvOrExpr(
            payload(offset = 1, mask = 0x3F, shift = 12),
            mkBvOrExpr(payload(offset = 2, mask = 0x3F, shift = 6), payload(offset = 3, mask = 0x3F, shift = 0)),
        ),
    )
    val rune4OrError = mkIte(fourBytes, rune4, mkBv(REPLACEMENT_RUNE))
    val multibyteRune = mkIte(twoBytes, rune2, mkIte(threeBytes, rune3, rune4OrError))
    val rune = mkIte(ascii, first, multibyteRune)

    val fourByteOrInvalidWidth = mkIte(fourBytes, mkSizeExpr(FOUR_BYTE_WIDTH), mkSizeExpr(1))
    val longWidth = mkIte(threeBytes, mkSizeExpr(THREE_BYTE_WIDTH), fourByteOrInvalidWidth)
    val width = mkIte(twoBytes, mkSizeExpr(2), longWidth)

    rune to width
}
