package org.usvm.machine

import io.ksmt.utils.asExpr
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.char
import org.jacodb.api.jvm.ext.findClassOrNull
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.jvmSignature
import org.usvm.UBoolExpr
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.allocateStringFromBvArray
import org.usvm.api.allocateStringFromCharArray
import org.usvm.api.allocateStringLiteral
import org.usvm.api.charAt
import org.usvm.api.concat
import org.usvm.api.contentOfString
import org.usvm.api.copyString
import org.usvm.api.readString
import org.usvm.api.stringEq
import org.usvm.api.stringLength
import org.usvm.api.substring
import org.usvm.api.toLower
import org.usvm.api.toUpper
import org.usvm.collection.array.length.UArrayLengthLValue
import org.usvm.collection.string.UStringLValue
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.mkSizeExpr
import org.usvm.mkSizeGeExpr
import org.usvm.mkSizeLeExpr
import org.usvm.mkSizeSubExpr
import org.usvm.sizeSort

class JcStringApproximations(private val ctx: JcContext) {
    private var currentScope: JcStepScope? = null
    private val scope: JcStepScope
        get() = checkNotNull(currentScope)

    private var currentExprResolver: JcExprResolver? = null
    private val exprResolver: JcExprResolver
        get() = checkNotNull(currentExprResolver)

    private val byteArrayType by lazy { ctx.cp.arrayTypeOf(ctx.cp.byte) }
    private val charArrayType by lazy { ctx.cp.arrayTypeOf(ctx.cp.char) }
    private val intArrayType by lazy { ctx.cp.arrayTypeOf(ctx.cp.int) }

    fun approximateStringOperation(
        scope: JcStepScope,
        exprResolver: JcExprResolver,
        callJcInst: JcMethodCall
    ): Boolean {
        this.currentScope = scope
        this.currentExprResolver = exprResolver
        val methodApproximation = stringClassApproximations[callJcInst.method.jvmSignature]
            ?: return false
                // TODO: remove it before PR-ing
                .also { println("WARNING: string approx for ${callJcInst.method.jvmSignature} not defined!") }
        val result = methodApproximation(callJcInst) ?: return true
        scope.doWithState { skipMethodInvocationWithValue(callJcInst, result) }
        return true
    }


    private fun MutableMap<String, (JcMethodCall) -> UExpr<*>?>.dispatchMethod(
        name: String,
        body: (JcMethodCall) -> UExpr<*>?,
    ) {
        this[name] = body
    }

//    private fun MutableMap<String, (JcMethodCall) -> UExpr<*>?>.dispatchMethod(
//        method: KFunction<*>,
//        body: (JcMethodCall) -> UExpr<*>?,
//    ) {
//        val methodName = method.javaMethod?.name ?: error("No name for $method")
//        dispatchMethod(methodName, body)
//    }
//
//    private fun MutableMap<String, (JcMethodCall) -> UExpr<*>?>.dispatchProperty(
//        prop: KProperty<*>,
//        body: (JcMethodCall) -> UExpr<*>?,
//    ) {
//        dispatchMethod(prop.name, body)
//    }
//
//    private fun MutableMap<String, (JcMethodCall) -> UExpr<*>?>.dispatchConstructor(
//        signature: String,
//        body: (JcMethodCall) -> UExpr<*>?,
//    ) {
//        dispatchMethod(CONSTRUCTOR + signature, body)
//    }

    /**
     * Forks on 0 <= [from] <= [to] <= length, throws an exception with type [exceptionType] in else branch.
     * If [exceptionType] is null, throws [StringIndexOutOfBoundsException].
     */
    private fun checkFromToIndex(
        from: UExpr<USizeSort>,
        to: UExpr<USizeSort>,
        length: UExpr<USizeSort>,
        exceptionType: JcRefType? = null
    ) = with(ctx) {
        val inside =
            (mkBvSignedLessOrEqualExpr(mkBv(0), from)) and
                    (mkBvSignedLessOrEqualExpr(from, to)) and
                    (mkBvSignedLessOrEqualExpr(to, length))

        scope.fork(
            inside,
            blockOnFalseState = exprResolver.allocateException(exceptionType ?: stringIndexOutOfBoundsExceptionType)
        )
    }

    /**
     * Forks on [from] >= 0 && [size] >= 0 && [length] >= 0 && [from] + [size] <= [length],
     * throws an exception with type [exceptionType] in else branch.
     * If [exceptionType] is null, throws [StringIndexOutOfBoundsException].
     */
    private fun checkFromIndexSize(
        from: UExpr<USizeSort>,
        size: UExpr<USizeSort>,
        length: UExpr<USizeSort>,
        exceptionType: JcRefType? = null
    ) = with(ctx) {
        val inside =
            (mkBvSignedLessOrEqualExpr(mkBv(0), from)) and
                    (mkBvSignedLessOrEqualExpr(mkBv(0), size)) and
                    (mkBvSignedLessOrEqualExpr(mkBv(0), length)) and
                    (mkBvSignedLessOrEqualExpr(size, mkBvSubExpr(length, from)))

        scope.fork(
            inside,
            blockOnFalseState = exprResolver.allocateException(exceptionType ?: stringIndexOutOfBoundsExceptionType)
        )
    }


    private fun allocStringFromBvArray(
        state: JcState,
        stringRef: UConcreteHeapRef,
        arrayRef: UHeapRef,
        type: JcType,
        sort: UBvSort,
        offset: UExpr<USizeSort>,
        length: UExpr<USizeSort>,
    ): UHeapRef? {
        exprResolver.checkNullPointer(arrayRef) ?: return null
        checkFromToIndex(offset, length, length, ctx.stringIndexOutOfBoundsExceptionType) ?: return null
        return state.memory.allocateStringFromBvArray(
            ctx.stringType,
            type,
            charArrayType,
            sort,
            offset,
            length,
            arrayRef,
            stringRef
        )
    }

    private fun allocStringFromBvArray(
        state: JcState,
        stringRef: UConcreteHeapRef,
        arrayRef: UHeapRef,
        type: JcType,
        sort: UBvSort,
    ): UHeapRef? {
        val offset = ctx.mkSizeExpr(0)
        val length = state.memory.read(UArrayLengthLValue(arrayRef, byteArrayType, ctx.sizeSort))

        return allocStringFromBvArray(state, stringRef, arrayRef, type, sort, offset, length)
    }

    private fun allocStringFromCharArray(
        state: JcState,
        stringRef: UConcreteHeapRef,
        arrayRef: UHeapRef,
        offset: UExpr<USizeSort>,
        length: UExpr<USizeSort>,
    ): UHeapRef? {
        exprResolver.checkNullPointer(arrayRef) ?: return null
        checkFromToIndex(offset, length, length, ctx.arrayIndexOutOfBoundsExceptionType) ?: return null
        return state.memory.allocateStringFromCharArray(
            ctx.stringType,
            charArrayType,
            arrayRef,
            offset,
            length,
            stringRef
        )
    }

    private fun allocStringFromCharArray(state: JcState, stringRef: UConcreteHeapRef, arrayRef: UHeapRef): UHeapRef? {
        exprResolver.checkNullPointer(arrayRef) ?: return null
        return state.memory.allocateStringFromCharArray<_, USizeSort>(
            ctx.stringType,
            charArrayType,
            arrayRef,
            stringRef,
        )
    }

    private fun startsWith(
        state: JcState,
        stringRef: UHeapRef,
        prefixRef: UHeapRef,
        toffset: UExpr<USizeSort>
    ): UBoolExpr? {
        exprResolver.checkNullPointer(prefixRef) ?: return null
        val thisLength = state.memory.stringLength<USizeSort>(stringRef)
        val prefixLength = state.memory.stringLength<USizeSort>(prefixRef)
        // toffset >= 0 && toffset <= length() - prefix.length()
        val offsetIsOk = ctx.mkAnd(
            ctx.mkSizeGeExpr(toffset, ctx.mkSizeExpr(0)),
            ctx.mkSizeLeExpr(toffset, ctx.mkSizeSubExpr(thisLength, prefixLength)),
        )
        if (offsetIsOk.isFalse)
            return ctx.falseExpr

        val substring = state.memory.substring(stringRef, ctx.stringType, toffset, prefixLength)
        val stringEq = state.memory.stringEq(substring, prefixRef)
        return ctx.mkAnd(offsetIsOk, stringEq)
    }

    private val stringClassApproximations: Map<String, (JcMethodCall) -> UExpr<*>?> by lazy {
        buildMap {
            dispatchMethod("<init>()V") {
                scope.calcOnState { memory.allocateStringLiteral(ctx.stringType, "") }
            }
            dispatchMethod("<init>(Ljava/lang/AbstractStringBuilder;Ljava/lang/Void;)V") {
                // This constructor is package private, all its usages should be approximated.
                // It is used only in StringBuffer and StringBuilder.
                error("This should not be called")
            }
            dispatchMethod("<init>(Ljava/lang/String;)V") {
                val copiedString = it.arguments.single().asExpr(ctx.addressSort)
                exprResolver.checkNullPointer(copiedString) ?: return@dispatchMethod null
                scope.calcOnState { memory.copyString(ctx.stringType, copiedString) }
            }
            // Constructor of StringBuffer is not approximated
            dispatchMethod("<init>(Ljava/lang/StringBuilder;)V") { methodCall ->
                val thisString = methodCall.arguments[0].asExpr(ctx.addressSort)
                val stringBuilderRef = methodCall.arguments[1].asExpr(ctx.addressSort)
                val returnSite = JcApproximatedReturnSiteInst(methodCall.returnSite) { scope, _ ->
                    scope.doWithState {
                        val methodResult = methodResult
                        check(methodResult is JcMethodResult.Success) { "Exception should have been processed earlier!" }
                        val value = methodResult.value.asExpr(ctx.addressSort)
                        memory.write(UStringLValue(thisString), memory.readString(value), ctx.trueExpr)
                        skipMethodInvocationWithValue(methodCall, ctx.voidValue)
                    }
                }
                scope.doWithState {
                    val stringBuilderClass =
                        ctx.cp.findClassOrNull<StringBuilder>() ?: error("StringBuilder was not resolved!")
                    val stringBuilderToStringMethod =
                        stringBuilderClass.declaredMethods.first { it.name == "toString" && it.parameters.isEmpty() }
                    newStmt(
                        JcConcreteMethodCallInst(
                            methodCall.location,
                            stringBuilderToStringMethod,
                            listOf(stringBuilderRef),
                            returnSite
                        )
                    )
                }
                null
            }
            dispatchMethod("<init>([B)V") {
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                scope.calcOnState {
                    allocStringFromBvArray(this, stringRef, arrayRef, byteArrayType, ctx.byteSort)
                }
            }
            dispatchMethod("<init>([BB)V") {
                // Package private constructor, used only in Integer, Long, ..., StringConcatHelper, StringUtf8, ...
                // Ignoring the coder for now
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                scope.calcOnState {
                    allocStringFromBvArray(this, stringRef, arrayRef, byteArrayType, ctx.byteSort)
                }
            }
            dispatchMethod("<init>([BI)V") {
                // Ignoring the hibyte for now
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                scope.calcOnState {
                    allocStringFromBvArray(this, stringRef, arrayRef, byteArrayType, ctx.byteSort)
                }
            }
            dispatchMethod("<init>([BII)V") {
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                val offset = it.arguments[2].asExpr(ctx.sizeSort)
                val length = it.arguments[3].asExpr(ctx.sizeSort)
                scope.calcOnState {
                    allocStringFromBvArray(
                        this,
                        stringRef,
                        arrayRef,
                        byteArrayType,
                        ctx.byteSort,
                        offset,
                        length
                    )
                }
            }
            dispatchMethod("<init>([BIII)V") {
                // Ignoring the hibyte for now
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                val offset = it.arguments[3].asExpr(ctx.sizeSort)
                val length = it.arguments[4].asExpr(ctx.sizeSort)
                scope.calcOnState {
                    allocStringFromBvArray(
                        this,
                        stringRef,
                        arrayRef,
                        byteArrayType,
                        ctx.byteSort,
                        offset,
                        length
                    )
                }
            }
            dispatchMethod("<init>([BIILjava/lang/String;)V") {
                // Ignoring the charset for now
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                val offset = it.arguments[2].asExpr(ctx.sizeSort)
                val length = it.arguments[3].asExpr(ctx.sizeSort)
                scope.calcOnState {
                    allocStringFromBvArray(
                        this,
                        stringRef,
                        arrayRef,
                        byteArrayType,
                        ctx.byteSort,
                        offset,
                        length
                    )
                }
            }
            dispatchMethod("<init>([BIILjava/nio/charset/Charset;)V") {
                // Ignoring the charset for now
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                val offset = it.arguments[2].asExpr(ctx.sizeSort)
                val length = it.arguments[3].asExpr(ctx.sizeSort)
                scope.calcOnState {
                    allocStringFromBvArray(
                        this,
                        stringRef,
                        arrayRef,
                        byteArrayType,
                        ctx.byteSort,
                        offset,
                        length,

                        )
                }
            }
            dispatchMethod("<init>([BLjava/lang/String;)V") {
                // Ignoring the charset for now
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                scope.calcOnState {
                    allocStringFromBvArray(this, stringRef, arrayRef, byteArrayType, ctx.byteSort)
                }
            }
            dispatchMethod("<init>([BLjava/nio/charset/Charset;)V") {
                // Ignoring the charset for now
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                scope.calcOnState {
                    allocStringFromBvArray(this, stringRef, arrayRef, byteArrayType, ctx.byteSort)
                }
            }
            dispatchMethod("<init>([C)V") {
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                scope.calcOnState {
                    allocStringFromCharArray(this, stringRef, arrayRef)
                }
            }
            dispatchMethod("<init>([CII)V") {
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                val offset = it.arguments[2].asExpr(ctx.sizeSort)
                val length = it.arguments[3].asExpr(ctx.sizeSort)
                scope.calcOnState {
                    allocStringFromCharArray(this, stringRef, arrayRef, offset, length)
                }
            }
            dispatchMethod("<init>([CIILjava/lang/Void;)V") {
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                val offset = it.arguments[2].asExpr(ctx.sizeSort)
                val length = it.arguments[3].asExpr(ctx.sizeSort)
                scope.calcOnState {
                    allocStringFromCharArray(this, stringRef, arrayRef, offset, length)
                }
            }
            dispatchMethod("<init>([III)V") {
                val stringRef = it.arguments[0].asExpr(ctx.addressSort) as UConcreteHeapRef
                val arrayRef = it.arguments[1].asExpr(ctx.addressSort)
                val offset = it.arguments[2].asExpr(ctx.sizeSort)
                val length = it.arguments[3].asExpr(ctx.sizeSort)
                scope.calcOnState {
                    allocStringFromBvArray(this, stringRef, arrayRef, intArrayType, ctx.bv32Sort, offset, length)
                }
            }
            dispatchMethod("charAt(I)C") {
                val stringRef = it.arguments[0].asExpr(ctx.addressSort)
                val index = it.arguments[1].asExpr(ctx.sizeSort)
                scope.calcOnState {
                    val length = memory.stringLength<USizeSort>(stringRef)
                    exprResolver.checkArrayIndex(index, length, ctx.stringIndexOutOfBoundsExceptionType)
                        ?: return@calcOnState null
                    memory.charAt(stringRef, index)
                }
            }
            dispatchMethod("chars()Ljava/util/stream/IntStream;") {
                TODO()
            }
            dispatchMethod("checkBoundsBeginEnd(III)V") {
                val from = it.arguments[0].asExpr(ctx.sizeSort)
                val to = it.arguments[1].asExpr(ctx.sizeSort)
                val length = it.arguments[2].asExpr(ctx.sizeSort)
                checkFromToIndex(from, to, length) ?: return@dispatchMethod null
                ctx.voidValue
            }
            dispatchMethod("checkBoundsOffCount(III)V") {
                val offset = it.arguments[0].asExpr(ctx.sizeSort)
                val count = it.arguments[1].asExpr(ctx.sizeSort)
                val length = it.arguments[2].asExpr(ctx.sizeSort)
                checkFromIndexSize(offset, count, length) ?: return@dispatchMethod null
                ctx.voidValue
            }
            dispatchMethod("checkIndex(II)V") {
                val index = it.arguments[0].asExpr(ctx.sizeSort)
                val length = it.arguments[1].asExpr(ctx.sizeSort)
                exprResolver.checkArrayIndex(index, length)
                ctx.voidValue
            }
            // Check offset is not approximated
            dispatchMethod("codePointAt(I)I") {
                TODO()
            }
            dispatchMethod("codePointBefore(I)I") {
                TODO()
            }
            dispatchMethod("codePointCount(II)I") {
                TODO()
            }
            dispatchMethod("codePoints()Ljava/util/stream/IntStream;") {
                TODO()
            }
            dispatchMethod("coder()B") {
                // Package private getter, used only in AbstractStringBuilder and StringConcatHelper
                // TODO: other coders?
                scope.calcOnState { ctx.mkBv(0, ctx.byteSort) }
            }
            dispatchMethod("compare(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)I") {
                TODO()
            }
            // compareTo(Object) is not approximated
            dispatchMethod("compareTo(Ljava/lang/String;)I") {
                TODO()
            }
            dispatchMethod("compareToIgnoreCase(Ljava/lang/String;)I") {
                TODO()
            }
            dispatchMethod("concat(Ljava/lang/String;)Ljava/lang/String;") {
                val left = it.arguments[0].asExpr(ctx.addressSort)
                val right = it.arguments[1].asExpr(ctx.addressSort)
                scope.calcOnState { memory.concat<JcType, USort>(ctx.stringType, left, right) }
            }
            dispatchMethod("contains(Ljava/lang/CharSequence;)Z") {
                TODO()
            }
            dispatchMethod("contentEquals(Ljava/lang/CharSequence;)Z") {
                TODO()
            }
            dispatchMethod("contentEquals(Ljava/lang/StringBuffer;)Z") {
                TODO()
            }
            dispatchMethod("copyValueOf([C)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("copyValueOf([CII)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("decodeASCII([BI[CII)I") {
                // Package private method, used only in JavaLangService
                TODO()
            }
            // describeConstable() is not approximated
            // endsWith(String) is not approximated
            dispatchMethod("equals(Ljava/lang/Object;)Z") {
                val thisRef = it.arguments[0].asExpr(ctx.addressSort)
                val objectRef = it.arguments[1].asExpr(ctx.addressSort)
                val referenceEquals = ctx.mkHeapRefEq(thisRef, objectRef)
                if (referenceEquals.isTrue) {
                    referenceEquals
                } else {
                    val objectIsNonNull = ctx.mkNot(ctx.mkHeapRefEq(objectRef, ctx.nullRef))
                    if (objectIsNonNull.isFalse)
                        ctx.falseExpr
                    val objectIsString = scope.calcOnState { memory.types.evalIsSubtype(objectRef, ctx.stringType) }
                    if (objectIsString.isFalse)
                        ctx.falseExpr
                    else {
                        val contentEq = scope.calcOnState { memory.stringEq(thisRef, objectRef) }
                        ctx.mkAnd(objectIsNonNull, objectIsString, contentEq)
                    }
                }
            }
            dispatchMethod("equalsIgnoreCase(Ljava/lang/String;)Z") {
                TODO()
            }
            dispatchMethod("format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("format(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("formatted([Ljava/lang/Object;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("getBytes()[B") {
                TODO()
            }
            dispatchMethod("getBytes(II[BI)V") {
                TODO()
            }
            dispatchMethod("getBytes(Ljava/lang/String;)[B") {
                TODO()
            }
            dispatchMethod("getBytes(Ljava/nio/charset/Charset;)[B") {
                TODO()
            }
            dispatchMethod("getBytes([BIB)V") {
                // Package private method, used only in AbstractStringBuilder and StringConcatHelper
                TODO()
            }
            dispatchMethod("getBytes([BIIBI)V") {
                // Package private method, used only in AbstractStringBuilder
                TODO()
            }
            dispatchMethod("getBytesNoRepl(Ljava/lang/String;Ljava/nio/charset/Charset;)[B") {
                // Package private method, used only in JavaLangAccess
                TODO()
            }
            dispatchMethod("getBytesUTF8NoRepl(Ljava/lang/String;)[B") {
                // Package private method, used only in JavaLangAccess
                TODO()
            }
            dispatchMethod("getChars(II[CI)V") {
                TODO()
            }
            dispatchMethod("getClass()Ljava/lang/Class;") {
                TODO()
            }
            dispatchMethod("hashCode()I") {
                TODO()
            }
            dispatchMethod("indent(I)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("indexOf(I)I") {
                TODO()
            }
            dispatchMethod("indexOf(II)I") {
                TODO()
            }
            dispatchMethod("indexOf(Ljava/lang/String;)I") {
                TODO()
            }
            dispatchMethod("indexOf(Ljava/lang/String;I)I") {
                TODO()
            }
            dispatchMethod("indexOf([BBILjava/lang/String;I)I") {
                // Package private method, used only in AbstractStringBuilder
                TODO()
            }
            dispatchMethod("intern()Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("isBlank()Z") {
                TODO()
            }
            dispatchMethod("isEmpty()Z") {
                TODO()
            }
            dispatchMethod("isLatin1()Z") {
                // Package private method, used only in AbstractStringBuilder
                TODO()
            }
            dispatchMethod("join(Ljava/lang/CharSequence;Ljava/lang/Iterable;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("join(Ljava/lang/CharSequence;[Ljava/lang/CharSequence;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("join(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;I)Ljava/lang/String;") {
                // Package private method, used only in JavaLangAccess
                TODO()
            }
            dispatchMethod("lastIndexOf(I)I") {
                TODO()
            }
            dispatchMethod("lastIndexOf(II)I") {
                TODO()
            }
            dispatchMethod("lastIndexOf(Ljava/lang/String;)I") {
                TODO()
            }
            dispatchMethod("lastIndexOf(Ljava/lang/String;I)I") {
                TODO()
            }
            dispatchMethod("lastIndexOf([BBILjava/lang/String;I)I") {
                // Package private method, used only in AbstractStringBuilder
                TODO()
            }
            dispatchMethod("length()I") {
                scope.calcOnState { memory.stringLength<USizeSort>(it.arguments.single().asExpr(ctx.addressSort)) }
            }
            dispatchMethod("lines()Ljava/util/stream/Stream;") {
                TODO()
            }
            dispatchMethod("matches(Ljava/lang/String;)Z") {
                TODO()
            }
            dispatchMethod("newStringNoRepl([BLjava/nio/charset/Charset;)Ljava/lang/String;") {
                // Package private method, used only in JavaLangAccess
                TODO()
            }
            dispatchMethod("newStringUTF8NoRepl([BII)Ljava/lang/String;") {
                // Package private method, used only in JavaLangAccess
                TODO()
            }
            dispatchMethod("offsetByCodePoints(II)I") {
                TODO()
            }
            dispatchMethod("regionMatches(ILjava/lang/String;II)Z") {
                TODO()
            }
            dispatchMethod("regionMatches(ZILjava/lang/String;II)Z") {
                TODO()
            }
            dispatchMethod("repeat(I)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("replace(CC)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("replaceAll(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("replaceFirst(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("resolveConstantDesc(Ljava/lang/invoke/MethodHandles\$Lookup;)Ljava/lang/Object;") {
                TODO()
            }
            dispatchMethod("resolveConstantDesc(Ljava/lang/invoke/MethodHandles\$Lookup;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("split(Ljava/lang/String;)[Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("split(Ljava/lang/String;I)[Ljava/lang/String;") {
                TODO()
            }
            // startsWith(String) is not approximated
            dispatchMethod("startsWith(Ljava/lang/String;I)Z") {
                val thisRef = it.arguments[0].asExpr(ctx.addressSort)
                val prefixRef = it.arguments[1].asExpr(ctx.addressSort)
                val toffset = it.arguments[2].asExpr(ctx.sizeSort)
                scope.calcOnState { startsWith(this, thisRef, prefixRef, toffset) }
            }
            dispatchMethod("strip()Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("stripIndent()Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("stripLeading()Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("stripTrailing()Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("subSequence(II)Ljava/lang/CharSequence;") {
                TODO()
            }
            // substring(from) is not approximated
            dispatchMethod("substring(II)Ljava/lang/String;") {
                val thisRef = it.arguments[0].asExpr(ctx.addressSort)
                val beginIndex = it.arguments[1].asExpr(ctx.sizeSort)
                val endIndex = it.arguments[2].asExpr(ctx.sizeSort)
                val length = scope.calcOnState { memory.stringLength<USizeSort>(thisRef) }
                checkFromToIndex(beginIndex, endIndex, length) ?: return@dispatchMethod null
                val substringLength = ctx.mkSizeSubExpr(endIndex, beginIndex)
                scope.calcOnState { memory.substring(thisRef, ctx.stringType, beginIndex, substringLength) }
            }
            dispatchMethod("toCharArray()[C") {
                val stringRef = it.arguments.single().asExpr(ctx.addressSort)
                scope.calcOnState { memory.contentOfString(stringRef, charArrayType, ctx.sizeSort, ctx.charSort) }
            }
            dispatchMethod("toLowerCase()Ljava/lang/String;") {
                val stringRef = it.arguments.single().asExpr(ctx.addressSort)
                scope.calcOnState { memory.toLower(ctx.stringType, stringRef) }
            }
            dispatchMethod("toLowerCase(Ljava/util/Locale;)Ljava/lang/String;") {
                // TODO: do not ignore locale
                val stringRef = it.arguments.single().asExpr(ctx.addressSort)
                scope.calcOnState { memory.toLower(ctx.stringType, stringRef) }
            }
            // toString() is not approximated
            dispatchMethod("toUpperCase()Ljava/lang/String;") {
                val stringRef = it.arguments.single().asExpr(ctx.addressSort)
                scope.calcOnState { memory.toUpper(ctx.stringType, stringRef) }
            }
            dispatchMethod("toUpperCase(Ljava/util/Locale;)Ljava/lang/String;") {
                // TODO: do not ignore locale
                val stringRef = it.arguments.single().asExpr(ctx.addressSort)
                scope.calcOnState { memory.toUpper(ctx.stringType, stringRef) }

            }
            dispatchMethod("transform(Ljava/util/function/Function;)Ljava/lang/Object;") {
                TODO()
            }
            dispatchMethod("translateEscapes()Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("trim()Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("value()[B") {
                // Package private method, used only in AbstractStringBuilder
                TODO()
            }
            dispatchMethod("valueOf(C)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("valueOf(D)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("valueOf(F)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("valueOf(I)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("valueOf(J)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("valueOf(Ljava/lang/Object;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("valueOf(Z)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("valueOf([C)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("valueOf([CII)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("valueOfCodePoint(I)Ljava/lang/String;") {
                TODO()
            }
        }
    }
}
