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

/** Go range decodes one UTF-8 rune; each malformed byte produces RuneError with width one. */
internal fun GoState.decodeUtf8Rune(
    string: UHeapRef,
    index: UExpr<USizeSort>,
    length: UExpr<USizeSort>,
): Pair<UExpr<UBv32Sort>, UExpr<USizeSort>> = with(ctx) {
    val bytes = (0 until 4).map { offset ->
        val byte = memory.readGoArrayIndex(string, mkSizeAddExpr(index, mkSizeExpr(offset)), GoBasicTypes.STRING, bv8Sort)
        mkBvZeroExtensionExpr(extensionSize = 24, value = byte).asExpr(bv32Sort)
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
        mkOr(mkNot(mkEq(first, mkBv(0xE0))), between(bytes[1], low = 0xA0, high = 0xBF)),
        mkOr(mkNot(mkEq(first, mkBv(0xED))), between(bytes[1], low = 0x80, high = 0x9F)),
    )
    val threeBytes = mkAnd(
        mkSizeGeExpr(remaining, mkSizeExpr(3)),
        between(first, low = 0xE0, high = 0xEF),
        validThreeByteSecond,
        continuation(offset = 2),
    )
    val validFourByteSecond = mkAnd(
        continuation(offset = 1),
        mkOr(mkNot(mkEq(first, mkBv(0xF0))), between(bytes[1], low = 0x90, high = 0xBF)),
        mkOr(mkNot(mkEq(first, mkBv(0xF4))), between(bytes[1], low = 0x80, high = 0x8F)),
    )
    val fourBytes = mkAnd(
        mkSizeGeExpr(remaining, mkSizeExpr(4)),
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
    val rune = mkIte(ascii, first, mkIte(twoBytes, rune2, mkIte(threeBytes, rune3, mkIte(fourBytes, rune4, mkBv(0xFFFD)))))
    val width = mkIte(twoBytes, mkSizeExpr(2), mkIte(threeBytes, mkSizeExpr(3), mkIte(fourBytes, mkSizeExpr(4), mkSizeExpr(1))))

    rune to width
}
