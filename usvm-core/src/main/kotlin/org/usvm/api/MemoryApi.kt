package org.usvm.api

import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.memory.UMemory
import org.usvm.memory.UReadOnlyMemory
import org.usvm.memory.UWritableMemory
import org.usvm.collection.array.UArrayIndexLValue
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.set.primitive.USetEntryLValue
import org.usvm.collection.set.ref.URefSetEntryLValue
import org.usvm.collection.string.URegexExpr
import org.usvm.collection.string.UStringExpr
import org.usvm.collection.string.UStringLValue
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
    type: ArrayType, sizeSort: USizeSort, count: UExpr<USizeSort>,
): UConcreteHeapRef = allocateArrayInternal(type, sizeSort, count)

fun <ArrayType, Sort : USort, USizeSort : USort> UWritableMemory<ArrayType>.allocateArrayInitialized(
    type: ArrayType, sort: Sort, sizeSort: USizeSort, contents: Sequence<UExpr<Sort>>
): UConcreteHeapRef = allocateArrayInitializedInternal(type, sort, sizeSort, contents)

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
fun <USizeSort: USort> UReadOnlyMemory<*>.readString(ref: UHeapRef): UStringExpr =
    read(UStringLValue<USizeSort>(ref))

fun <Type, USizeSort: USort> UWritableMemory<Type>.allocateStringLiteral(type: Type, string: String): UConcreteHeapRef {
    val freshRef = this.allocConcrete(type)
    val ctx = freshRef.uctx
    write(UStringLValue<USizeSort>(freshRef), ctx.mkStringLiteral(string), ctx.trueExpr)
    return freshRef
}

fun <Type, USizeSort: USort> UWritableMemory<Type>.allocateInternedStringLiteral(ctx: UContext<USizeSort>, type: Type, string: String): UConcreteHeapRef =
    ctx.internedStrings.getOrPut(string) {
        val freshRef = this.allocStatic(type)
        write(UStringLValue<USizeSort>(freshRef), ctx.mkStringLiteral(string), ctx.trueExpr)
        return freshRef
    }

fun UReadOnlyMemory<*>.allocateStringFromSequence(refToSeq: UHeapRef): UConcreteHeapRef =
    TODO()

fun <USizeSort: USort, UCharSort: USort> UReadOnlyMemory<*>.charAt(ref: UHeapRef, index: UExpr<USizeSort>): UExpr<UCharSort> =
    TODO()

fun <USizeSort: USort> UReadOnlyMemory<*>.stringLength(ref: UHeapRef): UExpr<USizeSort> =
    TODO()

/**
 * Allocates new string, which is the result of concatenation of [left] and [right].
 */
fun UReadOnlyMemory<*>.concat(left: UHeapRef, right: UHeapRef): UHeapRef =
    TODO()

fun UReadOnlyMemory<*>.stringEq(string1: UHeapRef, string2: UHeapRef): UBoolExpr =
    TODO()

fun UReadOnlyMemory<*>.stringLt(left: UHeapRef, right: UHeapRef): UBoolExpr =
    TODO()

fun UReadOnlyMemory<*>.stringLe(left: UHeapRef, right: UHeapRef): UBoolExpr =
    TODO()

/**
 * Allocates new string, which is the substring of [string], starting at index [startIndex] and having length [length].
 */
fun <USizeSort: USort> UReadOnlyMemory<*>.substring(string: UHeapRef, startIndex: UExpr<USizeSort>, length: UExpr<USizeSort>): UConcreteHeapRef =
    TODO()

/**
 * Allocates new string, which is the string representation of integer [value].
 */
fun <USizeSort: USort> UReadOnlyMemory<*>.stringFromInt(value: UExpr<USizeSort>): UConcreteHeapRef =
    TODO()

/**
 * Allocates new string, which is the string representation of float [value].
 */
fun <UFloatSort: USort> UReadOnlyMemory<*>.stringFromFloat(value: UExpr<UFloatSort>): UConcreteHeapRef =
    TODO()

/**
 * Parses string in heap location [ref]. Returns a list of pairs (success, value), where success is true iff string
 * represents some integer value. In those models, where success is true, value represents the integer
 * number encoded into the string. String is non-deterministic, the engine might return a list of such variants.
 */
fun <USizeSort: USort> UReadOnlyMemory<*>.tryParseIntFromString(ref: UHeapRef): List<Pair<UBoolExpr, UExpr<USizeSort>?>> =
    TODO()

/**
 * Parses string in heap location [ref]. Returns a list of pairs (success, value), where success is true iff string
 * represents some floating-point value. In those models, where success is true, value represents the floating-point
 * number encoded into the string. String is non-deterministic, the engine might return a list of such variants.
 */
fun <UFloatSort: USort> UReadOnlyMemory<*>.tryParseFloatFromString(ref: UHeapRef): List<Pair<UBoolExpr, UExpr<UFloatSort>?>> =
    TODO()

/**
 * Returns heap reference to new string obtained from string in heap location [ref]
 * by repeating it [times] amount of times. If [times] = 1, returns [ref].
 */
fun <USizeSort: USort> UReadOnlyMemory<*>.repeat(ref: UHeapRef, times: UExpr<USizeSort>): UHeapRef =
    TODO()

/**
 * Allocates new string, which is the upper-case variant of string referenced by [ref].
 */
fun UReadOnlyMemory<*>.toUpper(ref: UHeapRef): UHeapRef =
    TODO()

/**
 * Allocates new string, which is the lower-case variant of string referenced by [ref].
 */
fun UReadOnlyMemory<*>.toLower(ref: UHeapRef): UHeapRef =
    TODO()

/**
 * Allocates new string, which is the reverse of string referenced by [ref].
 */
fun UReadOnlyMemory<*>.reverse(ref: UHeapRef): UHeapRef =
    TODO()

/**
 * Returns index of the first occurrence of string referenced by [patternRef] into the string referenced by [stringRef].
 */
fun <USizeSort: USort> UReadOnlyMemory<*>.indexOf(stringRef: UHeapRef, patternRef: UHeapRef): UExpr<USizeSort> =
    TODO()

/**
 * Returns if string referenced by [stringRef] is matched by a regular expression [regex].
 */
fun UReadOnlyMemory<*>.matches(stringRef: UHeapRef, regex: URegexExpr): UBoolExpr =
    TODO()

/**
 * Returns a new string obtained by replacing in [where] the first occurrence of string [what] by [with].
 */
fun UReadOnlyMemory<*>.replaceFirst(where: UHeapRef, what: UHeapRef, with: UHeapRef): UHeapRef =
    TODO()

/**
 * Returns a new string obtained by replacing in [where] all occurrences of string [what] by [with].
 */
fun UReadOnlyMemory<*>.replaceAll(where: UHeapRef, what: UHeapRef, with: UHeapRef): UHeapRef =
    TODO()

/**
 * Returns new string obtained by replacing in [where] the first occurrence of regular expression [what] by [with].
 */
fun UReadOnlyMemory<*>.replaceFirstRegex(where: UHeapRef, what: URegexExpr, with: UHeapRef): UHeapRef =
    TODO()

/**
 * Returns new string obtained by replacing in [where] all occurrences of regular expression [what] by [with].
 */
fun UReadOnlyMemory<*>.replaceAllRegex(where: UHeapRef, what: URegexExpr, with: UHeapRef): UHeapRef =
    TODO()

//endregion