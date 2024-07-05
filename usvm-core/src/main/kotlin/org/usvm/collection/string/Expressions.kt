package org.usvm.collection.string

import io.ksmt.cache.hash
import io.ksmt.cache.structurallyEqual
import io.ksmt.expr.KExpr
import io.ksmt.expr.printer.ExpressionPrinter
import io.ksmt.expr.transformer.KTransformerBase
import io.ksmt.sort.KSortVisitor
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UCharSort
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.UTransformer
import org.usvm.asSizeTypedTransformer
import org.usvm.memory.USymbolicCollection
import org.usvm.memory.USymbolicCollectionId
import org.usvm.sizeSort
import org.usvm.uctx

interface USortVisitor<T>: KSortVisitor<T> {
    fun visit(sort: UStringSort): T
}

class UStringSort(ctx: UContext<*>): USort(ctx) {
    override fun <T> accept(visitor: KSortVisitor<T>): T {
        require(visitor is USortVisitor<T>) { "Expected a USortVisitor, but got: $visitor" }
        return visitor.visit(this)
    }

    override fun print(builder: StringBuilder) {
        builder.append("String")
    }
}

typealias UCharExpr = UExpr<UCharSort>
typealias UStringExpr = UExpr<UStringSort>

class UStringLiteralExpr internal constructor(
    ctx: UContext<*>,
    val s: String
): UStringExpr(ctx) {
    override val sort: UStringSort = ctx.stringSort

    override fun accept(transformer: KTransformerBase): KExpr<UStringSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) { s.hashCode() }

    override fun internHashCode(): Int = s.hashCode()

    override fun print(printer: ExpressionPrinter) {
        printer.append("\"$s\"")
    }
}

class UStringFromCollectionExpr<USizeSort: USort> internal constructor(
    val collection: USymbolicCollection<USymbolicCollectionId<UExpr<USizeSort>, UCharSort, *>, UExpr<USizeSort>, UCharSort>,
    val length: UExpr<USizeSort>
): UStringExpr(collection.sort.ctx) {
    override val sort = uctx.stringSort

    override fun accept(transformer: KTransformerBase): KExpr<UStringSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asSizeTypedTransformer<USizeSort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) { collection }

    override fun internHashCode(): Int = collection.hashCode()

    override fun print(printer: ExpressionPrinter) {
        printer.append(collection.toString())
    }
}

class UStringFromLanguageExpr internal constructor(
    ctx: UContext<*>,
    // TODO: add formal language (automaton-based?) representing possible content of this string
    val ref: UHeapRef
): UStringExpr(ctx) {
    override val sort = ctx.stringSort

    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) { ref }

    override fun internHashCode(): Int = ref.hashCode()

    override fun print(printer: ExpressionPrinter) {
        printer.append("(symbolic string at ")
        printer.append(ref)
        printer.append(")")
    }
}

class UStringConcatExpr internal constructor(
    ctx: UContext<*>,
    val left: UStringExpr,
    val right: UStringExpr,
) : UStringExpr(ctx) {
    override val sort = left.sort

    override fun accept(transformer: KTransformerBase): KExpr<UStringSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, { left }, { right })

    override fun internHashCode(): Int = hash(left, right)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(concat ")
        printer.append(left)
        printer.append(" ")
        printer.append(right)
        printer.append(")")
    }

}

class UStringLengthExpr<USizeSort: USort> internal constructor(
    ctx: UContext<USizeSort>,
    val string: UStringExpr,
) : UExpr<USizeSort>(ctx) {

    override val sort: USizeSort = ctx.sizeSort

    override fun accept(transformer: KTransformerBase): KExpr<USizeSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asSizeTypedTransformer<USizeSort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) {string}

    override fun internHashCode(): Int = hash(string)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(length ")
        printer.append(string)
        printer.append(")")
    }
}

class UCharAtExpr<USizeSort: USort> internal constructor(
    val string: UStringExpr,
    val index: UExpr<USizeSort>
) : UCharExpr(string.ctx) {
    override val sort: UCharSort = uctx.charSort

    override fun accept(transformer: KTransformerBase): UCharExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asSizeTypedTransformer<USizeSort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) {string}

    override fun internHashCode(): Int = hash(string)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(at ")
        printer.append(string)
        printer.append(" ")
        printer.append(index)
        printer.append(")")
    }
}



class UStringEqExpr internal constructor(
    val left: UStringExpr,
    val right: UStringExpr,
) : UBoolExpr(left.ctx) {
    override val sort: UBoolSort = ctx.boolSort

    override fun accept(transformer: KTransformerBase): UBoolExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, {left}, {right})

    override fun internHashCode(): Int = hash(left, right)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(= ")
        printer.append(left)
        printer.append(" ")
        printer.append(right)
        printer.append(")")
    }
}

class UStringLtExpr internal constructor(
    val left: UStringExpr,
    val right: UStringExpr,
) : UBoolExpr(left.ctx) {
    override val sort: UBoolSort = ctx.boolSort

    override fun accept(transformer: KTransformerBase): UBoolExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, {left}, {right})

    override fun internHashCode(): Int = hash(left, right)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(< ")
        printer.append(left)
        printer.append(" ")
        printer.append(right)
        printer.append(")")
    }
}

class UStringLeExpr internal constructor(
    val left: UStringExpr,
    val right: UStringExpr,
) : UBoolExpr(left.ctx) {
    override val sort: UBoolSort = ctx.boolSort
    override fun accept(transformer: KTransformerBase): UBoolExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, {left}, {right})

    override fun internHashCode(): Int = hash(left, right)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(<= ")
        printer.append(left)
        printer.append(" ")
        printer.append(right)
        printer.append(")")
    }
}

/**
 * Represents substring of [superString], from index [startIndex] (inclusive), with length [length].
 */
class UStringSliceExpr<USizeSort: USort> internal constructor(
    val superString: UStringExpr,
    val startIndex: UExpr<USizeSort>,
    val length: UExpr<USizeSort>,
) : UStringExpr(superString.ctx) {
    override val sort = superString.sort

    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asSizeTypedTransformer<USizeSort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, {superString}, {startIndex}, {length})

    override fun internHashCode(): Int = hash(superString, startIndex, length)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(substr ")
        printer.append(superString)
        printer.append(" ")
        printer.append(startIndex)
        printer.append(" ")
        printer.append(length)
        printer.append(")")
    }
}

class UStringFromIntExpr<USizeSort: USort> internal constructor(
    val value: UExpr<USizeSort>,
    // Theoretically, this can be symbolic, but in practice we don't need this
    val radix: Int
) : UStringExpr(value.ctx) {
    override val sort: UStringSort = uctx.stringSort

    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asSizeTypedTransformer<USizeSort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) {value}

    override fun internHashCode(): Int = hash(value)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(stringFromInt_radix_$radix ")
        printer.append(value)
        printer.append(")")
    }
}

class UIntFromStringExpr<USizeSort: USort> internal constructor(
    ctx: UContext<*>,
    override val sort: USizeSort,
    val string: UStringExpr,
    val radix: Int
) : UExpr<USizeSort>(ctx) {
    override fun accept(transformer: KTransformerBase): UExpr<USizeSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asSizeTypedTransformer<USizeSort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) {string}

    override fun internHashCode(): Int = hash(string)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(intFromString_radix_$radix ")
        printer.append(string)
        printer.append(")")
    }
}

class UStringFromFloatExpr<UFloatSort: USort> internal constructor(
    val value: UExpr<UFloatSort>
) : UStringExpr(value.ctx) {
    override val sort: UStringSort = uctx.stringSort

    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) {value}

    override fun internHashCode(): Int = hash(value)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(stringFromFloat ")
        printer.append(value)
        printer.append(")")
    }
}

class UFloatFromStringExpr<UFloatSort: USort> internal constructor(
    ctx: UContext<*>,
    override val sort: UFloatSort,
    val string: UStringExpr
) : UExpr<UFloatSort>(ctx) {
    override fun accept(transformer: KTransformerBase): UExpr<UFloatSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) {string}

    override fun internHashCode(): Int = hash(string)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(floatFromString ")
        printer.append(string)
        printer.append(")")
    }
}

class UStringRepeatExpr<USizeSort: USort> internal constructor(
    val string: UStringExpr,
    val times: UExpr<USizeSort>
) : UStringExpr(string.ctx) {
    override val sort = string.sort

    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asSizeTypedTransformer<USizeSort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, {string}, {times})

    override fun internHashCode(): Int = hash(string, times)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(repeat ")
        printer.append(string)
        printer.append(" ")
        printer.append(times)
        printer.append(")")
    }
}

class UStringToUpperExpr internal constructor(
    val string: UStringExpr,
) : UStringExpr(string.ctx) {
    override val sort = string.sort

    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) {string}

    override fun internHashCode(): Int = hash(string)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(toUpper ")
        printer.append(string)
        printer.append(")")
    }
}

class UStringToLowerExpr internal constructor(
    val string: UStringExpr,
) : UStringExpr(string.ctx) {
    override val sort = string.sort

    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) {string}

    override fun internHashCode(): Int = hash(string)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(toLower ")
        printer.append(string)
        printer.append(")")
    }
}

class UCharToUpperExpr internal constructor(
    val char: UCharExpr,
) : UCharExpr(char.ctx) {
    override val sort = char.sort

    override fun accept(transformer: KTransformerBase): UCharExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) {char}

    override fun internHashCode(): Int = hash(char)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(toUpper ")
        printer.append(char)
        printer.append(")")
    }
}

class UCharToLowerExpr internal constructor(
    val char: UCharExpr,
) : UCharExpr(char.ctx) {
    override val sort = char.sort

    override fun accept(transformer: KTransformerBase): UCharExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) {char}

    override fun internHashCode(): Int = hash(char)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(toLower ")
        printer.append(char)
        printer.append(")")
    }
}

class UStringReverseExpr internal constructor(
    ctx: UContext<*>,
    val string: UStringExpr,
) : UStringExpr(ctx) {
    override val sort = string.sort

    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other) {string}

    override fun internHashCode(): Int = hash(string)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(reverse ")
        printer.append(string)
        printer.append(")")
    }
}

/**
 * Index of the first occurrence of [pattern] in [string].
 */
class UStringIndexOfExpr<USizeSort: USort> internal constructor(
    ctx: UContext<*>,
    override val sort: USizeSort,
    val string: UStringExpr,
    val pattern: UStringExpr,
) : UExpr<USizeSort>(ctx) {
    override fun accept(transformer: KTransformerBase): UExpr<USizeSort> {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.asSizeTypedTransformer<USizeSort>().transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, {string}, {pattern})

    override fun internHashCode(): Int = hash(string, pattern)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(indexOf ")
        printer.append(string)
        printer.append(" ")
        printer.append(pattern)
        printer.append(")")
    }
}

// TODO: make advanced hierarchy here
typealias URegexExpr = UStringExpr

class URegexMatchesExpr internal constructor(
    ctx: UContext<*>,
    override val sort: UBoolSort,
    val string: UStringExpr,
    val pattern: URegexExpr,
) : UBoolExpr(ctx) {
    override fun accept(transformer: KTransformerBase): UBoolExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, {string}, {pattern})

    override fun internHashCode(): Int = hash(string, pattern)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(matches ")
        printer.append(string)
        printer.append(" ")
        printer.append(pattern)
        printer.append(")")
    }
}

class UStringReplaceFirstExpr internal constructor(
    ctx: UContext<*>,
    val where: UStringExpr,
    val what: UStringExpr,
    val with: UStringExpr
) : UStringExpr(ctx) {
    override val sort = where.sort
    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, {where}, {what}, {with})

    override fun internHashCode(): Int = hash(where, what, with)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(replace ")
        printer.append(where)
        printer.append(" ")
        printer.append(what)
        printer.append(" ")
        printer.append(with)
        printer.append(")")
    }
}

class UStringReplaceAllExpr internal constructor(
    ctx: UContext<*>,
    val where: UStringExpr,
    val what: UStringExpr,
    val with: UStringExpr
) : UStringExpr(ctx) {
    override val sort = where.sort
    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, {where}, {what}, {with})

    override fun internHashCode(): Int = hash(where, what, with)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(replace_all ")
        printer.append(where)
        printer.append(" ")
        printer.append(what)
        printer.append(" ")
        printer.append(with)
        printer.append(")")
    }
}

class URegexReplaceFirstExpr internal constructor(
    ctx: UContext<*>,
    val where: UStringExpr,
    val what: URegexExpr,
    val with: UStringExpr
) : UStringExpr(ctx) {
    override val sort = where.sort
    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, {where}, {what}, {with})

    override fun internHashCode(): Int = hash(where, what, with)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(regex_replace ")
        printer.append(where)
        printer.append(" ")
        printer.append(what)
        printer.append(" ")
        printer.append(with)
        printer.append(")")
    }
}

class URegexReplaceAllExpr internal constructor(
    ctx: UContext<*>,
    val where: UStringExpr,
    val what: URegexExpr,
    val with: UStringExpr
) : UStringExpr(ctx) {

    override val sort = where.sort

    override fun accept(transformer: KTransformerBase): UStringExpr {
        require(transformer is UTransformer<*, *>) { "Expected a UTransformer, but got: $transformer" }
        return transformer.transform(this)
    }

    override fun internEquals(other: Any): Boolean = structurallyEqual(other, {where}, {what}, {with})

    override fun internHashCode(): Int = hash(where, what, with)

    override fun print(printer: ExpressionPrinter) {
        printer.append("(regex_replace_all ")
        printer.append(where)
        printer.append(" ")
        printer.append(what)
        printer.append(" ")
        printer.append(with)
        printer.append(")")
    }
}
