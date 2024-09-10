package org.usvm.collection.string

import io.ksmt.utils.cast
import org.usvm.UConcreteChar
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.USort
import org.usvm.character
import org.usvm.getIntValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeLtExpr
import org.usvm.mkSizeModExpr
import org.usvm.mkSizeMulExpr
import org.usvm.mkSizeSubExpr
import org.usvm.uctx


internal val UReadOnlyMemory<*>.stringRegion: UStringMemoryRegion
    get() {
        val regionId = UStringRegionId(this.ctx)
        return getRegion(regionId).cast()
    }

internal inline fun <Sort : USort> UReadOnlyMemory<*>.mapString(
    ref: UHeapRef,
    mapper: (UStringExpr) -> UExpr<Sort>
): UExpr<Sort> =
    this.stringRegion.mapString(ref, mapper)

private fun <USizeSort : USort> getConcreteLength(ctx: UContext<USizeSort>, expr: UStringExpr): Int? {
    when (expr) {
        is UStringLiteralExpr -> return expr.s.length
        is UStringFromCollectionExpr<*> -> {
            val sizedString: UStringFromCollectionExpr<USizeSort> = expr.cast()
            return ctx.getIntValue(sizedString.length)
        }

        is UStringConcatExpr -> {
            val leftLength = getConcreteLength(ctx, expr.left) ?: return null
            val rightLength = getConcreteLength(ctx, expr.right) ?: return null
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
            val repeatedLength = getConcreteLength(ctx, sizedString.string) ?: return null
            val times = ctx.getIntValue(sizedString.times) ?: return null
            return repeatedLength * times
        }

        is UStringToUpperExpr -> return getConcreteLength(ctx, expr.string)
        is UStringToLowerExpr -> return getConcreteLength(ctx, expr.string)
        is UStringReverseExpr -> return getConcreteLength(ctx, expr.string)
        is UStringReplaceFirstExpr -> {
            val whereLength = getConcreteLength(ctx, expr.where) ?: return null
            val whatLength = getLength(ctx, expr.what)
            val withLength = getLength(ctx, expr.with)
            return if (whatLength == withLength) whereLength else null
        }

        is UStringReplaceAllExpr -> {
            val whereLength = getConcreteLength(ctx, expr.where) ?: return null
            val whatLength = getLength(ctx, expr.what)
            val withLength = getLength(ctx, expr.with)
            return if (whatLength == withLength) whereLength else null
        }

        is UIteExpr<UStringSort> -> {
            val thenLen = getConcreteLength(ctx, expr.trueBranch) ?: return null
            val elseLen = getConcreteLength(ctx, expr.falseBranch) ?: return null
            return if (thenLen == elseLen) thenLen else null
        }

        else -> return null
    }
}

internal fun <USizeSort : USort> getLength(ctx: UContext<USizeSort>, expr: UStringExpr): UExpr<USizeSort> =
    when (expr) {
        is UStringLiteralExpr -> ctx.mkSizeExpr(expr.s.length)
        is UStringFromCollectionExpr<*> -> expr.length.cast()
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

internal fun <USizeSort : USort> charAt(
    ctx: UContext<USizeSort>,
    string: UStringExpr,
    index: UExpr<USizeSort>
): UCharExpr {
    when (string) {
        is UStringFromCollectionExpr<*> -> {
            val sizedString: UStringFromCollectionExpr<USizeSort> = string.cast()
            return sizedString.collection.read(index)
        }

        is UStringSliceExpr<*> -> {
            val superStringIndex = ctx.mkSizeAddExpr(index, string.length.cast())
            return charAt(ctx, string.superString, superStringIndex)
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

        is UStringConcatExpr -> {
            val concreteLengthOfLeft = getConcreteLength(ctx, string.left) ?: return ctx.mkCharAtExpr(string, index)
            if (concreteIndex < concreteLengthOfLeft)
                return charAt(ctx, string.left, index)
            return charAt(ctx, string.right, index)
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

//internal fun concatStrings(left: UStringExpr, right: UStringExpr) =
//    when {
//        left is UStringLiteralExpr && right is UStringLiteralExpr -> left.uctx.mkStringLiteral(left.s + right.s)
//        left is UStringFromCollectionExpr<*> && right is UStringFromCollectionExpr<*> -> {
//            left.collection.copyRange()
//        }
//    }

// Concat of (charAt s i) and (charAt s i+1) = substring(s, i, i+1)
// Concat of (substring s i j) and (charAt s j+1) = substring(s, i, j+1)


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
