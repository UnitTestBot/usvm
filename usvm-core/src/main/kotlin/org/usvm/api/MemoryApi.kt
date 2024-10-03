package org.usvm.api

import io.ksmt.sort.KFpSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteChar
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.USort
import org.usvm.character
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UWritableMemory
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.string.UCharExpr
import org.usvm.collection.string.URegexExpr
import org.usvm.collection.string.UStringExpr
import org.usvm.collection.string.UStringLValue
import org.usvm.collection.string.allocateStringExpr
import org.usvm.collection.string.charAt
import org.usvm.collection.string.concatStrings
import org.usvm.collection.string.getString
import org.usvm.collection.string.getSubstring
import org.usvm.collection.string.isStringEmpty
import org.usvm.collection.string.mapString
import org.usvm.collection.string.mkStringExprFromCharArray
import org.usvm.collection.string.repeat
import org.usvm.collection.string.reverse
import org.usvm.collection.string.stringCmp
import org.usvm.collection.string.stringToLower
import org.usvm.collection.string.stringToUpper
import org.usvm.getIntValue
import org.usvm.memory.USymbolicCollectionKeyInfo
import org.usvm.mkSizeAddExpr
import org.usvm.mkSizeExpr
import org.usvm.mkSizeSubExpr
import org.usvm.regions.Region
import org.usvm.types.UTypeStream
import org.usvm.uctx
import org.usvm.withSizeSort
import org.usvm.collection.array.memcpy as memcpyInternal
import org.usvm.collection.array.memset as memsetInternal
import org.usvm.collection.array.allocateArray as allocateArrayInternal
import org.usvm.collection.array.allocateArrayInitialized as allocateArrayInitializedInternal

fun <Type> UReadOnlyMemory<Type>.typeStreamOf(ref: UHeapRef): UTypeStream<Type> =
    types.getTypeStream(ref)

fun UContext<*>.allocateConcreteRef(): UConcreteHeapRef = mkConcreteHeapRef(addressCounter.freshAllocatedAddress())
fun UContext<*>.allocateStaticRef(): UConcreteHeapRef = mkConcreteHeapRef(addressCounter.freshStaticAddress())

fun <Field, Sort : USort> UReadOnlyMemory<*>.readField(
    ref: UHeapRef, field: Field, sort: Sort
): UExpr<Sort> = read(UFieldLValue(sort, ref, field))

fun <ArrayType, Sort : USort, USizeSort : USort> UReadOnlyMemory<*>.readArrayIndex(
    ref: UHeapRef, index: UExpr<USizeSort>, arrayType: ArrayType, sort: Sort
): UExpr<Sort> = read(UArrayIndexLValue(sort, ref, index, arrayType))

fun <ArrayType, USizeSort : USort> UReadOnlyMemory<*>.readArrayLength(
    ref: UHeapRef, arrayType: ArrayType, sizeSort: USizeSort
): UExpr<USizeSort> = read(UArrayLengthLValue(ref, arrayType, sizeSort))

fun <Field, Sort : USort> UWritableMemory<*>.writeField(
    ref: UHeapRef, field: Field, sort: Sort, value: UExpr<Sort>, guard: UBoolExpr
) = write(UFieldLValue(sort, ref, field), value, guard)

fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<*>.writeArrayIndex(
    ref: UHeapRef, index: UExpr<USizeSort>, type: ArrayType, sort: Sort, value: UExpr<Sort>, guard: UBoolExpr
) = write(UArrayIndexLValue(sort, ref, index, type), value, guard)

fun <ArrayType, USizeSort : USort> UWritableMemory<*>.writeArrayLength(
    ref: UHeapRef, size: UExpr<USizeSort>, arrayType: ArrayType, sizeSort: USizeSort
) = write(UArrayLengthLValue(ref, arrayType, sizeSort), size, ref.uctx.trueExpr)


fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<*>.memcpy(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: ArrayType,
    elementSort: Sort,
    fromSrcIdx: UExpr<USizeSort>,
    fromDstIdx: UExpr<USizeSort>,
    toDstIdx: UExpr<USizeSort>,
    guard: UBoolExpr,
) {
    memcpyInternal(srcRef, dstRef, type, elementSort, fromSrcIdx, fromDstIdx, toDstIdx, guard)
}

fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<*>.memcpy(
    srcRef: UHeapRef,
    dstRef: UHeapRef,
    type: ArrayType,
    elementSort: Sort,
    fromSrc: UExpr<USizeSort>,
    fromDst: UExpr<USizeSort>,
    length: UExpr<USizeSort>,
) {
    val toDst = srcRef.uctx.withSizeSort { mkSizeAddExpr(fromDst, mkSizeSubExpr(length, mkSizeExpr(1))) }
    memcpy(srcRef, dstRef, type, elementSort, fromSrc, fromDst, toDst, guard = srcRef.ctx.trueExpr)
}

fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<ArrayType>.memset(
    ref: UHeapRef,
    type: ArrayType,
    sort: Sort,
    sizeSort: USizeSort,
    contents: Sequence<UExpr<Sort>>
) {
    memsetInternal(ref, type, sort, sizeSort, contents)
}

fun <ArrayType, USizeSort : USort> UWritableMemory<ArrayType>.allocateArray(
    arrayType: ArrayType, sizeSort: USizeSort, count: UExpr<USizeSort>,
): UConcreteHeapRef = allocateArrayInternal(arrayType, sizeSort, count)

fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<ArrayType>.allocateArrayInitialized(
    arrayType: ArrayType, sort: Sort, sizeSort: USizeSort, contents: Sequence<UExpr<Sort>>
): UConcreteHeapRef = allocateArrayInitializedInternal(arrayType, sort, sizeSort, contents)

fun <SetType, ElemSort : USort, Reg : Region<Reg>> UWritableMemory<SetType>.setAddElement(
    ref: UHeapRef,
    element: UExpr<ElemSort>,
    setType: SetType,
    elementInfo: USymbolicCollectionKeyInfo<UExpr<ElemSort>, Reg>,
    guard: UBoolExpr,
) = write(USetEntryLValue(element.sort, ref, element, setType, elementInfo), ref.uctx.trueExpr, guard)

fun <SetType, ElemSort : USort, Reg : Region<Reg>> UWritableMemory<SetType>.setRemoveElement(
    ref: UHeapRef,
    element: UExpr<ElemSort>,
    setType: SetType,
    elementInfo: USymbolicCollectionKeyInfo<UExpr<ElemSort>, Reg>,
    guard: UBoolExpr,
) = write(USetEntryLValue(element.sort, ref, element, setType, elementInfo), ref.uctx.falseExpr, guard)

fun <SetType, ElemSort : USort, Reg : Region<Reg>> UReadOnlyMemory<SetType>.setContainsElement(
    ref: UHeapRef,
    element: UExpr<ElemSort>,
    setType: SetType,
    elementInfo: USymbolicCollectionKeyInfo<UExpr<ElemSort>, Reg>,
): UBoolExpr = read(USetEntryLValue(element.sort, ref, element, setType, elementInfo))

fun <SetType> UWritableMemory<SetType>.refSetAddElement(
    ref: UHeapRef,
    element: UHeapRef,
    setType: SetType,
    guard: UBoolExpr,
) = write(URefSetEntryLValue(ref, element, setType), ref.uctx.trueExpr, guard)

fun <SetType> UWritableMemory<SetType>.refSetRemoveElement(
    ref: UHeapRef,
    element: UHeapRef,
    setType: SetType,
    guard: UBoolExpr,
) = write(URefSetEntryLValue(ref, element, setType), ref.uctx.falseExpr, guard)

fun <SetType> UReadOnlyMemory<SetType>.refSetContainsElement(
    ref: UHeapRef,
    element: UHeapRef,
    setType: SetType,
): UBoolExpr = read(URefSetEntryLValue(ref, element, setType))

//region Strings

/**
 * Returns string referenced by heap reference [ref].
 */
fun UReadOnlyMemory<*>.readString(ref: UHeapRef): UStringExpr =
    read(UStringLValue(ref))

fun <Type> UWritableMemory<Type>.allocateStringLiteral(stringType: Type, string: String): UConcreteHeapRef =
    this.allocateStringExpr(stringType, ctx.mkStringLiteral(string))

fun <Type> UWritableMemory<Type>.copyString(stringType: Type, ref: UHeapRef): UConcreteHeapRef =
    this.allocateStringExpr(stringType, readString(ref))

fun <Type, USizeSort : USort> UWritableMemory<Type>.allocateInternedStringLiteral(
    ctx: UContext<USizeSort>,
    type: Type,
    string: String
): UConcreteHeapRef =
    ctx.internedStrings.getOrPut(string) {
        val freshRef = this.allocStatic(type)
        write(UStringLValue(freshRef), ctx.mkStringLiteral(string), ctx.trueExpr)
        return freshRef
    }

fun <Type, USizeSort : USort> UWritableMemory<Type>.allocateStringFromArray(
    stringType: Type,
    charArrayType: Type,
    refToCharArray: UHeapRef,
): UConcreteHeapRef {
    val string = this.mkStringExprFromCharArray<Type, USizeSort>(charArrayType, refToCharArray)
    return this.allocateStringExpr(stringType, string)
}

fun <USizeSort : USort> UReadOnlyMemory<*>.charAt(ref: UHeapRef, index: UExpr<USizeSort>): UCharExpr =
    mapString(ref) { charAt(ctx.withSizeSort(), it, index) }

fun <USizeSort : USort> UReadOnlyMemory<*>.stringLength(ref: UHeapRef): UExpr<USizeSort> =
    mapString(ref) { org.usvm.collection.string.getLength(ctx.withSizeSort(), it) }

fun <USizeSort : USort> UReadOnlyMemory<*>.getHashCode(ref: UHeapRef): UExpr<USizeSort> =
    mapString(ref) { org.usvm.collection.string.getHashCode(ctx.withSizeSort(), it) }

/**
 * Allocates new string, which is the result of concatenation of [left] and [right].
 */
fun <Type, USizeSort : USort> UWritableMemory<Type>.concat(
    stringType: Type,
    left: UHeapRef,
    right: UHeapRef
): UHeapRef {
    val leftString = getString(left)
    if (isStringEmpty(ctx, leftString))
        return right
    val rightString = getString(right)
    if (isStringEmpty(ctx, rightString))
        return left
    val concatenation = concatStrings<Type, USizeSort>(leftString, rightString)
    return this.allocateStringExpr(stringType, concatenation)
}

fun <USizeSort : USort> UReadOnlyMemory<*>.stringHashCode(ref: UHeapRef): UExpr<USizeSort> =
    org.usvm.collection.string.getHashCode(ctx.withSizeSort(), getString(ref))

//fun UReadOnlyMemory<*>.compareStrings(left: UHeapRef, right: UHeapRef) {
//    mapString(left) { leftStr ->
//        mapString(right) { rightStr ->
//            val less = stringCmp(leftStr, rightStr, true)
//            val eq = ctx.mkEq(left)
//        }
//    }
//}

fun UReadOnlyMemory<*>.stringLt(left: UHeapRef, right: UHeapRef): UBoolExpr =
    mapString(left) { leftStr ->
        mapString(right) { rightStr ->
            stringCmp(leftStr, rightStr, true)
        }
    }

fun UReadOnlyMemory<*>.stringLe(left: UHeapRef, right: UHeapRef): UBoolExpr =
    mapString(left) { leftStr ->
        mapString(right) { rightStr ->
            stringCmp(leftStr, rightStr, false)
        }
    }

private inline fun <Type> UWritableMemory<Type>.mapAndAllocString(
    stringRef: UHeapRef,
    stringType: Type,
    crossinline mapper: (UStringExpr) -> UStringExpr
): UHeapRef {
    var stringDidNotChange = true
    val substring = mapString(stringRef) { string ->
        mapper(string).also { if (string != it) stringDidNotChange = false }
    }
    return if (stringDidNotChange) stringRef else allocateStringExpr(stringType, substring)
}

/**
 * Allocates new string, which is the substring of [stringRef], starting at index [startIndex] and having length [length].
 */
fun <Type, USizeSort : USort> UWritableMemory<Type>.substring(
    stringRef: UHeapRef,
    stringType: Type,
    startIndex: UExpr<USizeSort>,
    length: UExpr<USizeSort>
) =
    mapAndAllocString(stringRef, stringType) { getSubstring(it, startIndex, length) }


/**
 * Allocates new string, which is the string representation of integer [value] in the specified [radix].
 */
fun <Type, USizeSort : USort> UWritableMemory<Type>.stringFromInt(stringType: Type, value: UExpr<USizeSort>, radix: Int): UConcreteHeapRef =
    allocateStringExpr(stringType, org.usvm.collection.string.stringFromInt(ctx.withSizeSort(), value, radix))

/**
 * Allocates new string, which is the string representation of float [value].
 */
fun <Type, UFloatSort : KFpSort> UWritableMemory<Type>.stringFromFloat(stringType: Type, value: UExpr<UFloatSort>): UConcreteHeapRef =
    allocateStringExpr(stringType, org.usvm.collection.string.stringFromFloat(ctx, value))

/**
 * Parses string in heap location [ref]. Returns a list of pairs (success, value), where success is true iff string
 * represents some integer value. In those models, where success is true, value represents the integer
 * number encoded into the string in the specified [radix].
 * If string is non-deterministic, the engine might return a list of such variants.
 */
@Suppress("UNUSED_PARAMETER")
fun <USizeSort : USort> UReadOnlyMemory<*>.tryParseIntFromString(ref: UHeapRef, radix: Int): List<Pair<UBoolExpr, UExpr<USizeSort>?>> {
    TODO()
}

/**
 * Parses string in heap location [ref]. Returns a list of pairs (success, value), where success is true iff string
 * represents some floating-point value. In those models, where success is true, value represents the floating-point
 * number encoded into the string. If string is non-deterministic, the engine might return a list of such variants.
 */
@Suppress("UNUSED_PARAMETER")
fun <UFloatSort : KFpSort> UReadOnlyMemory<*>.tryParseFloatFromString(ref: UHeapRef): List<Pair<UBoolExpr, UExpr<UFloatSort>?>> =
    TODO()

/**
 * Returns heap reference to new string obtained from string in heap location [ref]
 * by repeating it [times] amount of times. If [times] = 1, returns [ref].
 */
fun <Type, USizeSort : USort> UWritableMemory<Type>.repeat(
    ref: UHeapRef,
    stringType: Type,
    times: UExpr<USizeSort>
): UHeapRef {
    val ctx = ref.uctx.withSizeSort<USizeSort>()
    val concreteTimes = ctx.getIntValue(times)
    if (concreteTimes == 0)
        return allocateInternedStringLiteral(ctx, stringType, "")
    if (concreteTimes == 1)
        return ref
    return allocateStringExpr(stringType, mapString(ref) { repeat(it, times) })
}

/**
 * Allocates new string, which is the upper-case variant of string referenced by [ref].
 */
fun <Type> UWritableMemory<Type>.toUpper(stringType: Type, ref: UHeapRef): UHeapRef =
    mapAndAllocString(ref, stringType, ::stringToUpper)

/**
 * Allocates new string, which is the lower-case variant of string referenced by [ref].
 */
fun <Type> UWritableMemory<Type>.toLower(stringType: Type, ref: UHeapRef): UHeapRef =
    mapAndAllocString(ref, stringType, ::stringToLower)


/**
 * Returns lower-case version of [char].
 */
fun charToLower(char: UCharExpr): UCharExpr =
    when (char) {
        is UConcreteChar -> char.uctx.mkChar(char.character.lowercaseChar())
        is UIteExpr -> char.ctx.mkIte(char.condition, charToLower(char.trueBranch), charToLower(char.falseBranch))
        else -> char.uctx.mkCharToLowerExpr(char)
    }

/**
 * Returns upper-case version of [char].
 */
fun charToUpper(char: UCharExpr): UCharExpr =
    when (char) {
        is UConcreteChar -> char.uctx.mkChar(char.character.uppercaseChar())
        is UIteExpr -> char.ctx.mkIte(char.condition, charToUpper(char.trueBranch), charToUpper(char.falseBranch))
        else -> char.uctx.mkCharToUpperExpr(char)
    }

/**
 * Allocates new string, which is the reverse of string referenced by [ref].
 */
fun <Type> UWritableMemory<Type>.reverse(ref: UHeapRef, stringType: Type): UHeapRef =
    allocateStringExpr(stringType, mapString(ref) { reverse(it) })

/**
 * Returns index of the first occurrence of string referenced by [patternRef] into the string referenced by [stringRef].
 */
fun <USizeSort : USort> UReadOnlyMemory<*>.indexOf(stringRef: UHeapRef, patternRef: UHeapRef): UExpr<USizeSort> =
    mapString(stringRef) { string ->
        mapString(patternRef) { pattern ->
            org.usvm.collection.string.indexOf(ctx.withSizeSort(), string, pattern)
        }
    }

/**
 * Returns if string referenced by [stringRef] is matched by a regular expression [regex].
 */
fun UReadOnlyMemory<*>.matches(stringRef: UHeapRef, regex: URegexExpr): UBoolExpr =
    mapString(stringRef) { string -> org.usvm.collection.string.matches(ctx, string, regex) }

/**
 * Returns a new string obtained by replacing in [whereRef] the first occurrence of string [whatRef] by [withRef].
 */
fun <Type> UWritableMemory<Type>.replaceFirst(
    stringType: Type,
    whereRef: UHeapRef,
    whatRef: UHeapRef,
    withRef: UHeapRef
): UHeapRef =
    mapAndAllocString(whereRef, stringType) { where ->
        mapString(whatRef) { what ->
            mapString(withRef) { with ->
                org.usvm.collection.string.replaceFirst(where, what, with)
            }
        }
    }

/**
 * Returns a new string obtained by replacing in [whereRef] all occurrences of string [whatRef] by [withRef].
 */
fun <Type> UWritableMemory<Type>.replaceAll(
    stringType: Type,
    whereRef: UHeapRef,
    whatRef: UHeapRef,
    withRef: UHeapRef
): UHeapRef =
    mapAndAllocString(whereRef, stringType) { where ->
        mapString(whatRef) { what ->
            mapString(withRef) { with ->
                org.usvm.collection.string.replaceAll(where, what, with)
            }
        }
    }

/**
 * Returns new string obtained by replacing in [whereRef] the first occurrence of regular expression [what] by [withRef].
 */
fun <Type> UWritableMemory<Type>.replaceFirstRegex(
    stringType: Type,
    whereRef: UHeapRef,
    what: URegexExpr,
    withRef: UHeapRef
): UHeapRef =
    mapAndAllocString(whereRef, stringType) { where ->
        mapString(withRef) { with ->
            org.usvm.collection.string.replaceFirstRegex(where, what, with)
        }
    }

/**
 * Returns new string obtained by replacing in [whereRef] all occurrences of regular expression [what] by [withRef].
 */
fun <Type> UWritableMemory<Type>.replaceAllRegex(
    stringType: Type,
    whereRef: UHeapRef,
    what: URegexExpr,
    withRef: UHeapRef
): UHeapRef =
    mapAndAllocString(whereRef, stringType) { where ->
        mapString(withRef) { with ->
            org.usvm.collection.string.replaceAllRegex(where, what, with)
        }
    }

//endregion