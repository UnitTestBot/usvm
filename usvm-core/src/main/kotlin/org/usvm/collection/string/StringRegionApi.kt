package org.usvm.collection.string

import io.ksmt.expr.KFp32Value
import io.ksmt.expr.KFp64Value
import io.ksmt.sort.KFpSort
import io.ksmt.utils.cast
import io.ksmt.utils.uncheckedCast
import org.usvm.UBoolExpr
import org.usvm.UCharSort
import org.usvm.UConcreteChar
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.USort
import org.usvm.api.allocateArray
import org.usvm.api.memcpy
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.api.writeArrayIndex
import org.usvm.character
import org.usvm.collection.array.UAllocatedArray
import org.usvm.collection.array.UArrayRegion
import org.usvm.collection.array.UArrayRegionId
import org.usvm.getIntValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UWritableMemory
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeLeExpr
import org.usvm.mkSizeLtExpr
import org.usvm.mkSizeModExpr
import org.usvm.mkSizeMulExpr
import org.usvm.mkSizeSubExpr
import org.usvm.sizeSort
import org.usvm.uctx
import org.usvm.withSizeSort
import kotlin.math.min


internal val UReadOnlyMemory<*>.stringRegion: UStringMemoryRegion
    get() {
        val regionId = UStringRegionId(this.ctx)
        return getRegion(regionId).cast()
    }

internal fun <Type> UWritableMemory<Type>.allocateStringExpr(
    stringType: Type,
    expr: UStringExpr,
    ref: UConcreteHeapRef? = null
): UConcreteHeapRef {
    val targetRef = ref ?: this.allocConcrete(stringType)
    val ctx = targetRef.uctx
    write(UStringLValue(targetRef), expr, ctx.trueExpr)
    return targetRef
}

private fun <Type, USizeSort : USort> UReadOnlyMemory<*>.getConcreteCharArray(
    ctx: UContext<USizeSort>,
    startIndex: UExpr<USizeSort>,
    length: UExpr<USizeSort>,
    refToCharArray: UHeapRef,
    arrayType: Type
): CharArray? {
    val concreteStartIndex = ctx.getIntValue(startIndex) ?: return null
    val concreteLength = ctx.getIntValue(length) ?: return null
    // TODO: use UConcreteStringBuilder?
    val result = CharArray(concreteLength)
    for (i in 0 until concreteLength) {
        val index = ctx.mkSizeExpr(i + concreteStartIndex)
        val charExpr = this.readArrayIndex(refToCharArray, index, arrayType, ctx.charSort)
        val char = (charExpr as? UConcreteChar)?.character ?: return null
        result[i] = char
    }
    return result
}

private fun <Type, USizeSort : USort> UReadOnlyMemory<Type>.getAllocatedCharArray(
    charArrayType: Type,
    address: UConcreteHeapAddress
): UAllocatedArray<Type, UCharSort, USizeSort> {
    val arrayRegion = getRegion(
        UArrayRegionId<Type, UCharSort, USizeSort>(
            charArrayType,
            ctx.charSort
        )
    ) as UArrayRegion<Type, UCharSort, USizeSort>
    return arrayRegion.getAllocatedArray(charArrayType, ctx.charSort, address)
}

internal fun <Type, USizeSort : USort> UReadOnlyMemory<Type>.mkStringExprFromCharArray(
    charArrayType: Type,
    refToCharArray: UHeapRef,
    startIndex: UExpr<USizeSort>? = null,
    length: UExpr<USizeSort>? = null
): UStringExpr {
    val ctx = ctx.withSizeSort<USizeSort>()
    val actualLength = length ?: this.readArrayLength(refToCharArray, charArrayType, ctx.sizeSort)
    val actualStartIndex = startIndex ?: ctx.mkSizeExpr(0)
    val concreteCharArray = getConcreteCharArray(ctx, actualStartIndex, actualLength, refToCharArray, charArrayType)
    if (concreteCharArray != null) {
        return ctx.mkStringLiteral(String(concreteCharArray))
    }

    require(this is UWritableMemory<*>) { "String from non-concrete collections should not be allocated in read-only memory!" }
    this as UWritableMemory<Type>

    val stringContentArrayRef: UConcreteHeapRef = when {
        refToCharArray is UConcreteHeapRef && startIndex == null && length == null -> refToCharArray
        else -> {
            val charArray = this.allocateArray(charArrayType, ctx.sizeSort, actualLength)
            val zero = ctx.mkSizeExpr(0)
            memcpy(refToCharArray, charArray, charArrayType, ctx.charSort, actualStartIndex, zero, actualLength)
            charArray
        }
    }
    val stringContentArray = getAllocatedCharArray<Type, USizeSort>(charArrayType, stringContentArrayRef.address)
    // TODO: if string content array contains the only ranged update from string
    //  (i.e. its collectionId is UStringCollectionId), then return this string.
    return ctx.mkStringFromArray(stringContentArray, charArrayType, actualLength)
}

internal fun <Type, USizeSort : USort, ElementSort : USort> UWritableMemory<Type>.getStringContent(
    string: UStringExpr,
    arrayType: Type,
    sizeSort: USizeSort,
    elementSort: ElementSort,
    elementConverter: ((UExpr<UCharSort>) -> UExpr<ElementSort>)? = null
): UConcreteHeapRef {
    val length = getLength<USizeSort>(ctx.withSizeSort(), string)
    val resultBuffer = allocateArray(arrayType, sizeSort, length)
    copyStringContentToArray<_, _, USizeSort>(
        string,
        resultBuffer,
        arrayType,
        elementSort,
        ctx.trueExpr,
        elementConverter
    )
    return resultBuffer
}

internal fun UReadOnlyMemory<*>.getString(ref: UHeapRef): UStringExpr =
    this.stringRegion.getString(ref)

internal inline fun <Sort : USort> UReadOnlyMemory<*>.mapString(
    ref: UHeapRef,
    crossinline mapper: (UStringExpr) -> UExpr<Sort>
): UExpr<Sort> =
    this.stringRegion.mapString(ref, mapper)

internal fun <USizeSort : USort> getStringConcreteLength(ctx: UContext<USizeSort>, expr: UStringExpr): Int? {
    when (expr) {
        is UStringLiteralExpr -> return expr.s.length
        is UStringFromArrayExpr<*, *> -> {
            val sizedString: UStringFromArrayExpr<*, USizeSort> = expr.cast()
            return ctx.getIntValue(sizedString.length)
        }

        is UStringConcatExpr -> {
            val leftLength = getStringConcreteLength(ctx, expr.left) ?: return null
            val rightLength = getStringConcreteLength(ctx, expr.right) ?: return null
            return leftLength + rightLength
        }

        is UStringSliceExpr<*> -> {
            val sizedString: UStringSliceExpr<USizeSort> = expr.cast()
            return ctx.getIntValue(sizedString.length)
        }

        is UStringFromIntExpr<*> -> return null
        is UStringFromFloatExpr<*> -> return null
        is UStringRepeatExpr<*> -> {
            val sizedString: UStringRepeatExpr<USizeSort> = expr.cast()
            val repeatedLength = getStringConcreteLength(ctx, sizedString.string) ?: return null
            val times = ctx.getIntValue(sizedString.times) ?: return null
            return repeatedLength * times
        }

        is UStringToUpperExpr -> return getStringConcreteLength(
            ctx,
            expr.string
        ) // TODO: This is incorrect for some locales...
        is UStringToLowerExpr -> return getStringConcreteLength(
            ctx,
            expr.string
        ) // TODO: This is incorrect for some locales...
        is UStringReverseExpr -> return getStringConcreteLength(ctx, expr.string)
        is UStringReplaceFirstExpr -> {
            val whereLength = getStringConcreteLength(ctx, expr.where) ?: return null
            val whatLength = getLength(ctx, expr.what)
            val withLength = getLength(ctx, expr.with)
            return if (whatLength == withLength) whereLength else null
        }

        is UStringReplaceAllExpr -> {
            val whereLength = getStringConcreteLength(ctx, expr.where) ?: return null
            val whatLength = getLength(ctx, expr.what)
            val withLength = getLength(ctx, expr.with)
            return if (whatLength == withLength) whereLength else null
        }

        is UIteExpr<UStringSort> -> {
            val thenLen = getStringConcreteLength(ctx, expr.trueBranch) ?: return null
            val elseLen = getStringConcreteLength(ctx, expr.falseBranch) ?: return null
            return if (thenLen == elseLen) thenLen else null
        }

        else -> return null
    }
}

internal fun <USizeSort : USort> isStringEmpty(ctx: UContext<USizeSort>, expr: UStringExpr): Boolean =
    getStringConcreteLength(ctx, expr) == 0

internal fun <USizeSort : USort> getLength(ctx: UContext<USizeSort>, expr: UStringExpr): UExpr<USizeSort> =
    when (expr) {
        is UStringLiteralExpr -> ctx.mkSizeExpr(expr.s.length)
        is UStringFromArrayExpr<*, *> -> expr.length.cast()
        is UStringConcatExpr -> ctx.mkSizeAddExpr(getLength(ctx, expr.left), getLength(ctx, expr.right))
        is UStringSliceExpr<*> -> expr.length.cast()
        is UStringRepeatExpr<*> -> ctx.mkSizeMulExpr(getLength(ctx, expr.string), expr.times.cast())
        is UStringToUpperExpr -> getLength(ctx, expr.string).cast()
        is UStringToLowerExpr -> getLength(ctx, expr.string).cast()
        is UStringReverseExpr -> getLength(ctx, expr.string).cast()
        is UStringReplaceFirstExpr -> {
            val whatLength = getLength(ctx, expr.what)
            val withLength = getLength(ctx, expr.with)
            if (whatLength == withLength) getLength(ctx, expr.where) else ctx.mkStringLengthExpr(expr)
        }

        is UStringReplaceAllExpr -> {
            val whatLength = getLength(ctx, expr.what)
            val withLength = getLength(ctx, expr.with)
            if (whatLength == withLength) getLength(ctx, expr.where) else ctx.mkStringLengthExpr(expr)
        }

        else -> ctx.mkStringLengthExpr(expr)
    }

internal fun <USizeSort : USort> getHashCode(ctx: UContext<USizeSort>, expr: UStringExpr): UExpr<USizeSort> =
    when (expr) {
        // TODO: use another concrete hash code evaluation for non-JVM languages. It should be configured in UComponents.
        is UStringLiteralExpr -> ctx.mkConcreteStringHashCodeExpr(expr.s.hashCode(), expr)
        else -> ctx.mkStringHashCodeExpr(expr)
    }

internal fun <USizeSort : USort> charAt(
    ctx: UContext<USizeSort>,
    string: UStringExpr,
    index: UExpr<USizeSort>
): UCharExpr {
    when (string) {
        is UStringFromArrayExpr<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            string as UStringFromArrayExpr<*, USizeSort>
            return string.content.read(index)
        }

        is UStringSliceExpr<*> -> {
            val superStringIndex = ctx.mkSizeAddExpr(index, string.length.cast())
            return charAt(ctx, string.superString, superStringIndex)
        }

        is UStringConcatExpr -> {
            return ctx.mkIte(
                ctx.mkSizeLtExpr(index, getLength(ctx, string.left)),
                { charAt(ctx, string.left, index) },
                { charAt(ctx, string.right, index) })
        }

        is UStringReverseExpr -> {
            val one = ctx.mkSizeExpr(1)
            val revIndexPlusOne = ctx.mkSizeSubExpr(getLength(ctx, string.string), index)
            val revIndex = ctx.mkSizeSubExpr(revIndexPlusOne, one)
            return charAt(ctx, string.string, revIndex)
        }

        is UStringRepeatExpr<*> -> {
            val indexInsideRepeatedString = ctx.mkSizeModExpr(index, getLength(ctx, string.string))
            return charAt(ctx, string.string, indexInsideRepeatedString)
        }
    }
    val concreteIndex = ctx.getIntValue(index) ?: return ctx.mkCharAtExpr(string, index)
    when (string) {
        is UStringLiteralExpr -> {
            // This condition MUST be validated by interpreter before getting here
            require(0 <= concreteIndex && concreteIndex < string.s.length) { "String index out of range!" }
            return ctx.mkChar(string.s[concreteIndex])
        }

        is UStringFromIntExpr<*> -> {
            val value: UExpr<USizeSort> = string.value.cast()
            if (concreteIndex == 0) {
                return ctx.mkIte(
                    ctx.mkSizeLtExpr(value, ctx.mkSizeExpr(0)),
                    { ctx.mkChar('-') },
                    { ctx.mkCharAtExpr(string, index) })
            }
        }
    }
    return ctx.mkCharAtExpr(string, index)
}


@Suppress("UNCHECKED_CAST")
internal fun <Type, USizeSort : USort> UReadOnlyMemory<Type>.concatStrings(
    left: UStringExpr,
    right: UStringExpr
): UStringExpr {
    val ctx = this.ctx.withSizeSort<USizeSort>()
    return when {
        left is UStringLiteralExpr && right is UStringLiteralExpr -> ctx.mkStringLiteral(left.s + right.s)
        left is UStringFromArrayExpr<*, *> && right is UStringFromArrayExpr<*, *> -> {
            left as UStringFromArrayExpr<Type, USizeSort>
            right as UStringFromArrayExpr<Type, USizeSort>
            check(left.charArrayType == right.charArrayType)

            require(this is UWritableMemory<*>) { "Concatenation of collection-based strings should not be done in read-only memory!" }
            this as UWritableMemory<Type>
            val resultLength = ctx.mkSizeAddExpr(left.length, right.length)
            val resultCharArrayRef = this.allocateArray(left.charArrayType, ctx.sizeSort, resultLength)

            this.memcpy(
                srcRef = left.contentRef,
                dstRef = resultCharArrayRef,
                type = left.charArrayType,
                elementSort = ctx.charSort,
                fromSrc = ctx.mkSizeExpr(0),
                fromDst = ctx.mkSizeExpr(0),
                length = left.length
            )
            this.memcpy(
                srcRef = right.contentRef,
                dstRef = resultCharArrayRef,
                type = right.charArrayType,
                elementSort = ctx.charSort,
                fromSrc = ctx.mkSizeExpr(0),
                fromDst = left.length,
                length = right.length
            )

            val resultCharArray = getAllocatedCharArray<Type, USizeSort>(left.charArrayType, resultCharArrayRef.address)
            ctx.mkStringFromArray(resultCharArray, left.charArrayType, resultLength)
        }

        left is UStringRepeatExpr<*> && right is UStringRepeatExpr<*> && left.string == right.string -> {
            left as UStringRepeatExpr<USizeSort>
            right as UStringRepeatExpr<USizeSort>
            ctx.mkStringRepeatExpr(left.string, ctx.mkSizeAddExpr(left.times, right.times))
        }

        left is UStringSliceExpr<*> && right is UStringSliceExpr<*> && left.superString == right.superString -> {
            left as UStringSliceExpr<USizeSort>
            right as UStringSliceExpr<USizeSort>
            if (right.superString == ctx.mkSizeAddExpr(left.startIndex, left.length))
                ctx.mkStringSliceExpr(left.superString, left.startIndex, ctx.mkSizeAddExpr(left.length, right.length))
            else
                ctx.mkStringConcatExpr(left, right)
        }

        getStringConcreteLength(ctx, right) == 1 -> {
            val rightChar = charAt(ctx, right, ctx.mkSizeExpr(0))
            when {
                left is UStringFromArrayExpr<*, *> -> {
                    left as UStringFromArrayExpr<Type, USizeSort>
                    val resultLength = ctx.mkSizeAddExpr(left.length, ctx.mkSizeExpr(1))
                    require(this is UWritableMemory<*>) { "Concatenation of collection-based strings should not be done in read-only memory!" }
                    this as UWritableMemory<Type>
                    val resultCharArrayRef = this.allocateArray(left.charArrayType, ctx.sizeSort, resultLength)

                    this.memcpy(
                        srcRef = left.contentRef,
                        dstRef = resultCharArrayRef,
                        type = left.charArrayType,
                        elementSort = ctx.charSort,
                        fromSrc = ctx.mkSizeExpr(0),
                        fromDst = ctx.mkSizeExpr(0),
                        length = left.length
                    )
                    this.writeArrayIndex(
                        resultCharArrayRef,
                        left.length,
                        left.charArrayType,
                        ctx.charSort,
                        rightChar,
                        ctx.trueExpr
                    )
                    val resultCharArray =
                        getAllocatedCharArray<Type, USizeSort>(left.charArrayType, resultCharArrayRef.address)
                    ctx.mkStringFromArray(resultCharArray, left.charArrayType, resultLength)
                }

                left is UStringSliceExpr<*> && rightChar is UCharAtExpr<*> && left.superString == rightChar.string -> {
                    left as UStringSliceExpr<USizeSort>
                    if (ctx.mkSizeAddExpr(left.startIndex, left.length) == rightChar.index)
                        ctx.mkStringSliceExpr(
                            left.superString,
                            left.startIndex,
                            ctx.mkSizeAddExpr(left.length, ctx.mkSizeExpr(1))
                        )
                    else
                        ctx.mkStringConcatExpr(left, right)
                }

                else -> ctx.mkStringConcatExpr(left, right)
            }
        }

        getStringConcreteLength(ctx, left) == 1 -> {
            val leftChar = charAt(ctx, left, ctx.mkSizeExpr(0))
            when {
                right is UStringFromArrayExpr<*, *> -> {
                    right as UStringFromArrayExpr<Type, USizeSort>
                    val resultLength = ctx.mkSizeAddExpr(right.length, ctx.mkSizeExpr(1))
                    require(this is UWritableMemory<*>) { "Concatenation of collection-based strings should be done on a UWritableMemory" }
                    this as UWritableMemory<Type>
                    val resultCharArrayRef = this.allocateArray(right.charArrayType, ctx.sizeSort, resultLength)

                    this.writeArrayIndex(
                        resultCharArrayRef,
                        ctx.mkSizeExpr(0),
                        right.charArrayType,
                        ctx.charSort,
                        leftChar,
                        ctx.trueExpr
                    )
                    this.memcpy(
                        srcRef = right.contentRef,
                        dstRef = resultCharArrayRef,
                        type = right.charArrayType,
                        elementSort = ctx.charSort,
                        fromSrc = ctx.mkSizeExpr(0),
                        fromDst = ctx.mkSizeExpr(1),
                        length = right.length
                    )
                    val resultCharArray =
                        getAllocatedCharArray<Type, USizeSort>(right.charArrayType, resultCharArrayRef.address)
                    ctx.mkStringFromArray(resultCharArray, right.charArrayType, resultLength)
                }

                leftChar is UCharAtExpr<*> && right is UStringSliceExpr<*> && leftChar.string == right.superString -> {
                    leftChar as UCharAtExpr<USizeSort>
                    right as UStringSliceExpr<USizeSort>
                    if (ctx.mkSizeAddExpr(leftChar.index, ctx.mkSizeExpr(1)) == right.startIndex)
                        ctx.mkStringSliceExpr(
                            leftChar.string,
                            leftChar.index,
                            ctx.mkSizeAddExpr(right.length, ctx.mkSizeExpr(1))
                        )
                    else
                        ctx.mkStringConcatExpr(left, right)
                }

                else -> ctx.mkStringConcatExpr(left, right)
            }
        }

        else -> ctx.mkStringConcatExpr(left, right)
    }
}

private fun mkSymbolicStringCmp(left: UStringExpr, right: UStringExpr, isStrict: Boolean): UBoolExpr =
    if (isStrict)
        left.uctx.mkStringLtExpr(left, right)
    else
        left.uctx.mkStringLeExpr(left, right)

internal fun stringEq(left: UStringExpr, right: UStringExpr) =
    left.uctx.mkEq(left, right)

/**
 * Returns "[left] lexicographically less than [right]". If [isStrict] is false, then "less or equal".
 */
internal fun stringCmp(left: UStringExpr, right: UStringExpr, isStrict: Boolean): UBoolExpr {
    when {
        left is UStringLiteralExpr && right is UStringLiteralExpr -> {
            val cmp = left.s.compareTo(right.s)
            return left.ctx.mkBool(if (isStrict) cmp < 0 else cmp <= 0)
        }

        else -> {
            val ctx = left.uctx.withSizeSort<USort>()
            val len1 = getLength(ctx, left)
            val len2 = getLength(ctx, right)
            val concreteLen1 = ctx.getIntValue(len1)
            val concreteLen2 = ctx.getIntValue(len2)
            val min =
                if (concreteLen1 == null) concreteLen2
                else if (concreteLen2 == null) concreteLen1
                else min(concreteLen1, concreteLen2)
            if (min == null) {
                return mkSymbolicStringCmp(left, right, isStrict)
            }
            for (i in 0 until min) {
                val index = ctx.mkSizeExpr(i)
                val c1 = charAt(ctx, left, index)
                val c2 = charAt(ctx, right, index)
                if (c1 is UConcreteChar && c2 is UConcreteChar) {
                    val cmp = c1.character.compareTo(c2.character)
                    if (cmp != 0) {
                        return ctx.mkBool(cmp < 0)
                    }
                } else {
                    return mkSymbolicStringCmp(left, right, isStrict)
                }
            }
            return if (isStrict) ctx.mkSizeLtExpr(len1, len2) else ctx.mkSizeLeExpr(len1, len2)
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <USizeSort : USort> UReadOnlyMemory<*>.getSubstring(
    string: UStringExpr,
    from: UExpr<USizeSort>,
    length: UExpr<USizeSort>
): UStringExpr {
    val ctx = this.ctx.withSizeSort<USizeSort>()
    val stringLength = getLength(ctx, string)
    if (length == ctx.mkSizeExpr(0) && length == stringLength) {
        return string
    }
    when (string) {
        is UStringLiteralExpr -> {
            val concreteFrom = ctx.getIntValue(from)
            val concreteLength = ctx.getIntValue(length)
            if (concreteFrom != null && concreteLength != null) {
                return ctx.mkStringLiteral(string.s.substring(concreteFrom, concreteFrom + concreteLength))
            }
        }

        is UStringFromArrayExpr<*, *> -> {
            string as UStringFromArrayExpr<Any, USizeSort>
            require(this is UWritableMemory<*>) { "Slicing of collection-based strings should not be done in read-only memory!" }
            this as UWritableMemory<Any>
            val resultCharArrayRef = this.allocateArray(string.charArrayType, ctx.sizeSort, length)

            this.memcpy(
                srcRef = string.contentRef,
                dstRef = resultCharArrayRef,
                type = string.charArrayType,
                elementSort = ctx.charSort,
                fromSrc = from,
                fromDst = ctx.mkSizeExpr(0),
                length = length
            )

            val resultCharArray =
                getAllocatedCharArray<Any, USizeSort>(string.charArrayType, resultCharArrayRef.address)
            ctx.mkStringFromArray(resultCharArray, string.charArrayType, length)
        }

        is UStringConcatExpr -> {
            val concreteFrom = ctx.getIntValue(from)
            if (concreteFrom != null) {
                val leftLen = getStringConcreteLength(ctx, string.left)
                if (leftLen != null) {
                    if (concreteFrom >= leftLen) {
                        // If i >= len(s1), then (s1 + s2)[i..i+l] = s2[i-len(s1) .. i-len(s1)+l]
                        return getSubstring(string.right, ctx.mkSizeExpr(concreteFrom - leftLen), length)
                    }
                    val concreteLength = ctx.getIntValue(length)
                    if (concreteLength != null && concreteFrom + concreteLength < leftLen) {
                        // If i+l < len(s1), then (s1 + s2)[i..i+l] = s1[i .. i+l]
                        return getSubstring(string.left, from, length)
                    }
                }
            }
        }

        is UStringSliceExpr<*> -> {
            // s[i .. i+l1][j .. j+l2] = s[i+j .. i+j+min(l1,l2)]
            string as UStringSliceExpr<USizeSort>
            val newFrom = ctx.mkSizeAddExpr(string.startIndex, from)
            val newLength = ctx.mkIte(ctx.mkSizeLeExpr(string.length, length), string.length, length)
            return getSubstring(string.superString, newFrom, newLength)
        }

        is UStringRepeatExpr<*> -> {
            val concreteFrom = ctx.getIntValue(from)
            val concreteLength = ctx.getIntValue(length)
            if (concreteFrom != null && concreteLength != null) {
                val concretePatternLength = getStringConcreteLength(ctx, string.string)
                if (concretePatternLength != null &&
                    concreteFrom % concretePatternLength == 0 &&
                    concreteLength % concretePatternLength == 0
                ) {
                    // If i % len(s) = 0 and l % len(s) = 0, then
                    // repeat(s, n)[i, i..l] = repeat(s, min(l/len(s), n-i/len(s)))
                    val lenTimes = ctx.mkSizeExpr(concreteLength / concretePatternLength)
                    val nTimes = ctx.mkSizeSubExpr(
                        string.times.uncheckedCast(),
                        ctx.mkSizeExpr(concreteFrom / concretePatternLength)
                    )
                    val newTimes = ctx.mkIte(ctx.mkSizeLeExpr(lenTimes, nTimes), lenTimes, nTimes)
                    return repeat(string, newTimes)
                }
            }
        }

        is UStringReverseExpr -> {
            // rev(s)[i .. i+l] = rev(s[len(s) - i - l, len(s) - i])
            val newFrom = ctx.mkSizeSubExpr(ctx.mkSizeSubExpr(stringLength, from), length)
            val origSlice = getSubstring(string.string, newFrom, length)
            return reverse(origSlice)
        }

    }
    return ctx.mkStringSliceExpr(string, from, length)
}

internal fun <USizeSort : USort> repeat(string: UStringExpr, times: UExpr<USizeSort>): UStringExpr {
    val ctx = string.uctx.withSizeSort<USizeSort>()
    val concreteTimes = ctx.getIntValue(times)
    if (concreteTimes != null && concreteTimes <= 0)
        return ctx.mkEmptyString()
    if (concreteTimes == 1)
        return string
    when (string) {
        is UStringLiteralExpr ->
            if (concreteTimes != null)
                return ctx.mkStringLiteral(string.s.repeat(concreteTimes))

        is UStringRepeatExpr<*> ->
            ctx.mkStringRepeatExpr(string, ctx.mkSizeMulExpr(times, string.times.uncheckedCast()))
    }
    return ctx.mkStringRepeatExpr(string, times)
}

internal fun stringToUpper(string: UStringExpr): UStringExpr =
    when {
        string is UStringLiteralExpr -> string.uctx.mkStringLiteral(string.s.uppercase())
        string is UStringToUpperExpr || // TODO: can this be incorrect for some locales?
                string is UStringFromIntExpr<*> && string.radix <= 10 -> string

        else -> string.uctx.mkStringToUpperExpr(string)
    }

internal fun stringToLower(string: UStringExpr): UStringExpr =
    when {
        string is UStringLiteralExpr -> string.uctx.mkStringLiteral(string.s.lowercase())
        string is UStringToLowerExpr || // TODO: can this be incorrect for some locales?
                string is UStringFromIntExpr<*> && string.radix <= 10 -> string

        else -> string.uctx.mkStringToLowerExpr(string)
    }

internal fun reverse(string: UStringExpr): UStringExpr =
    when (string) {
        is UStringLiteralExpr -> string.uctx.mkStringLiteral(string.s.reversed())
        is UStringReverseExpr -> string.string
        else -> string.uctx.mkStringReverseExpr(string)
    }

internal fun <USizeSort : USort> indexOf(
    ctx: UContext<USizeSort>,
    string: UStringExpr,
    pattern: UStringExpr
): UExpr<USizeSort> =
    when {
        string == pattern -> ctx.mkSizeExpr(0)
        pattern is UStringLiteralExpr && pattern.s.isEmpty() -> ctx.mkSizeExpr(0)
        string is UStringLiteralExpr && pattern is UStringLiteralExpr ->
            ctx.mkSizeExpr(string.s.indexOf(pattern.s))

        else -> ctx.mkStringIndexOfExpr(string, pattern)
    }

internal fun matches(ctx: UContext<*>, string: UStringExpr, pattern: URegexExpr): UBoolExpr =
    when {
        pattern is UStringLiteralExpr && pattern.s.isEmpty() -> ctx.mkTrue()
        string is UStringLiteralExpr && pattern is UStringLiteralExpr ->
            // TODO: optimize regex'es up by storing compiled regex into expression
            ctx.mkBool(Regex(pattern.s).matches(string.s))

        else -> ctx.mkRegexMatchesExpr(string, pattern)
    }

internal fun replaceFirst(where: UStringExpr, what: UStringExpr, with: UStringExpr): UStringExpr =
    when {
        what == with -> where
        where is UStringLiteralExpr && what is UStringLiteralExpr && with is UStringLiteralExpr ->
            where.uctx.mkStringLiteral(where.s.replaceFirst(what.s, with.s))

        else ->
            where.uctx.mkStringReplaceFirstExpr(where, what, with)
    }

internal fun replaceAll(where: UStringExpr, what: UStringExpr, with: UStringExpr): UStringExpr =
    when {
        what == with -> where
        where is UStringLiteralExpr && what is UStringLiteralExpr && with is UStringLiteralExpr ->
            where.uctx.mkStringLiteral(where.s.replace(what.s, with.s))

        else ->
            where.uctx.mkStringReplaceAllExpr(where, what, with)
    }

internal fun replaceFirstRegex(where: UStringExpr, what: URegexExpr, with: UStringExpr): UStringExpr =
    when {
        where is UStringLiteralExpr && what is UStringLiteralExpr && with is UStringLiteralExpr ->
            where.uctx.mkStringLiteral(where.s.replaceFirst(Regex(what.s), with.s))

        else ->
            where.uctx.mkRegexReplaceFirstExpr(where, what, with)
    }

internal fun replaceAllRegex(where: UStringExpr, what: URegexExpr, with: UStringExpr): UStringExpr =
    when {
        where is UStringLiteralExpr && what is UStringLiteralExpr && with is UStringLiteralExpr ->
            where.uctx.mkStringLiteral(where.s.replace(Regex(what.s), with.s))

        else ->
            where.uctx.mkRegexReplaceAllExpr(where, what, with)
    }

internal fun <USizeSort : USort> stringFromInt(
    ctx: UContext<USizeSort>,
    value: UExpr<USizeSort>,
    radix: Int
): UStringExpr {
    // TODO: decompose ite's in [value]?
    val concreteValue = ctx.getIntValue(value)
    if (concreteValue != null)
        return ctx.mkStringLiteral(concreteValue.toString(radix))
    return ctx.mkStringFromIntExpr(value, radix)
}

internal fun <UFloatSort : KFpSort> stringFromFloat(ctx: UContext<*>, value: UExpr<UFloatSort>): UStringExpr =
    // TODO: decompose ite's in [value]?
    when (value) {
        is KFp32Value -> ctx.mkStringLiteral(value.value.toString())
        is KFp64Value -> ctx.mkStringLiteral(value.value.toString())
        else -> ctx.mkStringFromFloatExpr(value)
    }

//when (expr) {
//    is UStringLiteralExpr -> {
//    }
//    is UStringFromArrayExpr<*, *> -> {
//    }
//    is UStringConcatExpr -> {
//    }
//    is UStringSliceExpr<*> -> {
//    }
//    is UStringFromIntExpr<*> -> {
//    }
//    is UStringFromFloatExpr<*> -> {
//    }
//    is UStringRepeatExpr<*> -> {
//    }
//    is UStringToUpperExpr -> {
//    }
//    is UStringToLowerExpr -> {
//    }
//    is UStringReverseExpr -> {
//    }
//    is UStringReplaceFirstExpr -> {
//    }
//    is UStringReplaceAllExpr -> {
//    }
//    is URegexReplaceFirstExpr -> {
//    }
//    is URegexReplaceAllExpr -> {
//    }
//}

// TODO: We might consider to introduce an option to reason only about ASCII strings.
//       With this option enabled, we might add more simplifications:
//       1. length(s) = length(s.toUpper()) == length(s.toLower())
//       2. concat(s1.toUpper(), s2.toUpper()) = concat(s1, s2).toUpper()
//       3. string.toUpper() <= string and string <= string.toLower()
//       4. string.toUpper().toUpper() = string.toUpper(), string.toLower().toUpper() = string.toUpper(), etc.
//       5. repeat(s.toUpper(), n) = repeat(s, n).toUpper() (?)
//       6. slice(s.toUpper(), i, n) = slice(s, i, n).toUpper()
//       7. new string(array).toUpper()[i] == charToUpper(array[i])
//       8. reverse(s.toUpper()) = reverse(s).toUpper()
//       (same for toLower())