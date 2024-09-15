package org.usvm.collection.string

import io.ksmt.utils.cast
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
import org.usvm.mkSizeLtExpr
import org.usvm.mkSizeModExpr
import org.usvm.mkSizeMulExpr
import org.usvm.mkSizeSubExpr
import org.usvm.sizeSort
import org.usvm.uctx
import org.usvm.withSizeSort


internal val UReadOnlyMemory<*>.stringRegion: UStringMemoryRegion
    get() {
        val regionId = UStringRegionId(this.ctx)
        return getRegion(regionId).cast()
    }

internal fun <Type> UWritableMemory<Type>.allocateStringExpr(stringType: Type, expr: UStringExpr): UConcreteHeapRef {
    val freshRef = this.allocConcrete(stringType)
    val ctx = freshRef.uctx
    write(UStringLValue(freshRef), expr, ctx.trueExpr)
    return freshRef
}

private fun <Type, USizeSort : USort> UReadOnlyMemory<*>.getConcreteCharArray(
    ctx: UContext<USizeSort>,
    length: UExpr<USizeSort>,
    refToCharArray: UHeapRef,
    arrayType: Type
): CharArray? {
    val concreteLength = ctx.getIntValue(length) ?: return null
    // TODO: use UConcreteStringBuilder?
    val result = CharArray(concreteLength)
    for (i in 0 until concreteLength) {
        val charExpr = this.readArrayIndex(refToCharArray, ctx.mkSizeExpr(i), arrayType, ctx.charSort)
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
    refToCharArray: UHeapRef
): UStringExpr {
    val ctx = ctx.withSizeSort<USizeSort>()
    val length = this.readArrayLength(refToCharArray, charArrayType, ctx.sizeSort)
    val concreteCharArray = getConcreteCharArray(ctx, length, refToCharArray, charArrayType)
    if (concreteCharArray != null) {
        return ctx.mkStringLiteral(String(concreteCharArray))
    }

    require(this is UWritableMemory<*>) { "String from non-concrete collections should not be allocated in read-only memory!" }
    this as UWritableMemory<Type>

    val stringContentArrayRef: UConcreteHeapRef = when (refToCharArray) {
        is UConcreteHeapRef -> refToCharArray
        else -> {
            val charArray = this.allocateArray(charArrayType, ctx.sizeSort, length)
            val zero = ctx.mkSizeExpr(0)
            memcpy(refToCharArray, charArray, charArrayType, ctx.charSort, zero, zero, length)
            charArray
        }
    }
    val stringContentArray = getAllocatedCharArray<Type, USizeSort>(charArrayType, stringContentArrayRef.address)
    return ctx.mkStringFromArray(stringContentArray, charArrayType, length)
}

internal fun UReadOnlyMemory<*>.getString(ref: UHeapRef): UStringExpr =
    this.stringRegion.getString(ref)

internal inline fun <Sort : USort> UReadOnlyMemory<*>.mapString(
    ref: UHeapRef,
    mapper: (UStringExpr) -> UExpr<Sort>
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

        is UStringToUpperExpr -> return getStringConcreteLength(ctx, expr.string)
        is UStringToLowerExpr -> return getStringConcreteLength(ctx, expr.string)
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
    getStringConcreteLength(ctx, expr).let { it == 0 }

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
        // TODO: use another concrete hash code evaluation for other languages?
        is UStringLiteralExpr -> ctx.mkSizeExpr(expr.s.hashCode())
        else -> ctx.mkStringHashCodeExpr(expr)
    }

internal fun <USizeSort : USort> UReadOnlyMemory<*>.charAt(
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

        getStringConcreteLength(ctx, right).let { it == 1 } -> {
            val rightChar = this.charAt(ctx, right, ctx.mkSizeExpr(0))
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
                    val resultCharArray = getAllocatedCharArray<Type, USizeSort>(left.charArrayType, resultCharArrayRef.address)
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

        getStringConcreteLength(ctx, left).let { it == 1 } -> {
            val leftChar = this.charAt(ctx, left, ctx.mkSizeExpr(0))
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
                    val resultCharArray = getAllocatedCharArray<Type, USizeSort>(right.charArrayType, resultCharArrayRef.address)
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

//when (expr) {
//    is UStringLiteralExpr -> {
//    }
//    is UStringFromCollectionExpr<*> -> {
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
