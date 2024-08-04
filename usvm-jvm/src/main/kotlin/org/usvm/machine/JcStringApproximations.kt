package org.usvm.machine

import io.ksmt.utils.asExpr
import org.jacodb.api.JcType
import org.jacodb.api.ext.findClassOrNull
import org.jacodb.api.ext.jvmSignature
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.allocateStringLiteral
import org.usvm.api.concat
import org.usvm.api.copyString
import org.usvm.api.readString
import org.usvm.api.stringLength
import org.usvm.api.toLower
import org.usvm.api.toUpper
import org.usvm.collection.string.UStringLValue
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.skipMethodInvocationWithValue

class JcStringApproximations(private val ctx: JcContext) {
    private var currentScope: JcStepScope? = null
    private val scope: JcStepScope
        get() = checkNotNull(currentScope)

    private var currentExprResolver: JcExprResolver? = null
    private val exprResolver: JcExprResolver
        get() = checkNotNull(currentExprResolver)

    fun approximateStringOperation(
        scope: JcStepScope,
        exprResolver: JcExprResolver,
        callJcInst: JcMethodCall
    ): Boolean {
        this.currentScope = scope
        this.currentExprResolver = exprResolver
        val methodApproximation = stringClassApproximations[callJcInst.method.jvmSignature]
            ?: return false.also { println("WARNING: string approx for ${callJcInst.method.jvmSignature} not defined!") }
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


    private val stringClassApproximations: Map<String, (JcMethodCall) -> UExpr<*>?> by lazy {
        buildMap {
            dispatchMethod("<init>()V") {
                scope.calcOnState { memory.allocateStringLiteral(ctx.stringType, "") }
            }
            dispatchMethod("<init>(Ljava/lang/AbstractStringBuilder;Ljava/lang/Void;)V") {
                // This constructor is package private, all its usages should be approximated.
                error("This should not be called")
            }
            dispatchMethod("<init>(Ljava/lang/String;)V") {
                // TODO: nullability checks?
                scope.calcOnState { memory.copyString(ctx.stringType, it.arguments.single().asExpr(ctx.addressSort)) }
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
                //JcUnaryOperator.CastToChar.onBv(ctx, ...)
                //ctx.mkBvSignExtensionExpr(8, this)
                TODO()
            }
            dispatchMethod("<init>([BB)V") {
                
                TODO()
            }
            dispatchMethod("<init>([BI)V") {
                TODO()
            }
            dispatchMethod("<init>([BII)V") {
                TODO()
            }
            dispatchMethod("<init>([BIII)V") {
                TODO()
            }
            dispatchMethod("<init>([BIILjava/lang/String;)V") {
                TODO()
            }
            dispatchMethod("<init>([BIILjava/nio/charset/Charset;)V") {
                TODO()
            }
            dispatchMethod("<init>([BLjava/lang/String;)V") {
                TODO()
            }
            dispatchMethod("<init>([BLjava/nio/charset/Charset;)V") {
                TODO()
            }
            dispatchMethod("<init>([C)V") {
                TODO()
            }
            dispatchMethod("<init>([CII)V") {
                TODO()
            }
            dispatchMethod("<init>([CIILjava/lang/Void;)V") {
                TODO()
            }
            dispatchMethod("<init>([III)V") {
                TODO()
            }
            dispatchMethod("charAt(I)C") {
                TODO()
            }
            dispatchMethod("chars()Ljava/util/stream/IntStream;") {
                TODO()
            }
            dispatchMethod("checkBoundsBeginEnd(III)V") {
                TODO()
            }
            dispatchMethod("checkBoundsOffCount(III)V") {
                TODO()
            }
            dispatchMethod("checkIndex(II)V") {
                TODO()
            }
            dispatchMethod("checkOffset(II)V") {
                TODO()
            }
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
                // TODO: other coders?
                scope.calcOnState { ctx.mkBv(0, ctx.byteSort) }
            }
            dispatchMethod("compare(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)I") {
                TODO()
            }
            // compareTo(Object) is not approximated
            dispatchMethod("compareTo(Ljava/lang/String;)I") {
                scope.calcOnState {  }
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
                TODO()
            }
            // describeConstable() is not approximated
            dispatchMethod("endsWith(Ljava/lang/String;)Z") {
                TODO()
            }
            dispatchMethod("equals(Ljava/lang/Object;)Z") {
                TODO()
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
                TODO()
            }
            dispatchMethod("getBytes([BIIBI)V") {
                TODO()
            }
            dispatchMethod("getBytesNoRepl(Ljava/lang/String;Ljava/nio/charset/Charset;)[B") {
                TODO()
            }
            dispatchMethod("getBytesUTF8NoRepl(Ljava/lang/String;)[B") {
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
                TODO()
            }
            dispatchMethod("join(Ljava/lang/CharSequence;Ljava/lang/Iterable;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("join(Ljava/lang/CharSequence;[Ljava/lang/CharSequence;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("join(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;I)Ljava/lang/String;") {
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
                TODO()
            }
            dispatchMethod("length()I") {
                scope.calcOnState { memory.stringLength<USort>(it.arguments.single().asExpr(ctx.addressSort)) }
            }
            dispatchMethod("lines()Ljava/util/stream/Stream;") {
                TODO()
            }
            dispatchMethod("matches(Ljava/lang/String;)Z") {
                TODO()
            }
            dispatchMethod("newStringNoRepl([BLjava/nio/charset/Charset;)Ljava/lang/String;") {
                TODO()
            }
            dispatchMethod("newStringUTF8NoRepl([BII)Ljava/lang/String;") {
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
            dispatchMethod("startsWith(Ljava/lang/String;)Z") {
                TODO()
            }
            dispatchMethod("startsWith(Ljava/lang/String;I)Z") {
                TODO()
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
                TODO()
            }
            dispatchMethod("toCharArray()[C") {
                TODO()
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

// 1. Exceptions
// 2. Lt -> cmp
// 3. adapters: byte to char, char to int, etc.
// 4. string to collection


/**
public String(char[] value)
public String(char[] value, int offset, int count)
public String(int[] codePoints, int offset, int count) {
public String(byte[] ascii, int hibyte, int offset, int count) // deprecated
public String(byte[] ascii, int hibyte) // deprecated
public String(byte[] bytes, int offset, int length, Charset charset)
public String(byte[] bytes, String charsetName) throws UnsupportedEncodingException
public String(byte[] bytes, Charset charset)
public String(byte[] bytes, int offset, int length)
public String(byte[] bytes)
public String(StringBuffer buffer)
public String(StringBuilder builder)
public int length()
public boolean isEmpty()
public char charAt(int index)
public int codePointAt(int index)
public int codePointBefore(int index)
public int codePointCount(int beginIndex, int endIndex)
public int offsetByCodePoints(int index, int codePointOffset)
public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin)
public void getBytes(int srcBegin, int srcEnd, byte[] dst, int dstBegin)
public byte[] getBytes(String charsetName) throws UnsupportedEncodingException {
public byte[] getBytes(Charset charset)
public byte[] getBytes()
public boolean equals(Object anObject)
public boolean contentEquals(StringBuffer sb)
public boolean contentEquals(CharSequence cs)
public boolean equalsIgnoreCase(String anotherString)
public int compareTo(String anotherString)
public int compareToIgnoreCase(String str)
public boolean regionMatches(int toffset, String other, int ooffset, int len)
public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len)
public boolean startsWith(String prefix, int toffset)
public boolean startsWith(String prefix)
public boolean endsWith(String suffix)
public int hashCode()
public int indexOf(int ch)
public int indexOf(int ch, int fromIndex)
public int lastIndexOf(int ch)
public int lastIndexOf(int ch, int fromIndex)
public int indexOf(String str)
public int indexOf(String str, int fromIndex)
public int lastIndexOf(String str)
public int lastIndexOf(String str, int fromIndex)
public String substring(int beginIndex)
public String substring(int beginIndex, int endIndex)
public CharSequence subSequence(int beginIndex, int endIndex)
public String concat(String str)
public String replace(char oldChar, char newChar)
public boolean matches(String regex)
public boolean contains(CharSequence s)
public String replaceFirst(String regex, String replacement)
public String replaceAll(String regex, String replacement)
public String replace(CharSequence target, CharSequence replacement)
public String[] split(String regex, int limit)
public String[] split(String regex)
public static String join(CharSequence delimiter, CharSequence... elements)
public static String join(CharSequence delimiter, Iterable<? extends CharSequence> elements)
public String toLowerCase(Locale locale)
public String toLowerCase()
public String toUpperCase(Locale locale)
public String toUpperCase()
public String trim()
public String strip()
public String stripLeading()
public String stripTrailing()
public boolean isBlank()
public Stream<String> lines()
public String indent(int n)
public String stripIndent()
public String translateEscapes()
public <R> R transform(Function<? super String, ? extends R> f)
public IntStream chars()
public IntStream codePoints()
public char[] toCharArray()
public static String format(String format, Object... args)
public static String format(Locale l, String format, Object... args)
public String formatted(Object... args)
public static String valueOf(Object obj)
public static String valueOf(char[] data)
public static String valueOf(char[] data, int offset, int count)
public static String copyValueOf(char[] data, int offset, int count)
public static String copyValueOf(char[] data)
public static String valueOf(boolean b)
public static String valueOf(char c)
public static String valueOf(int i)
public static String valueOf(long l)
public static String valueOf(float f)
public static String valueOf(double d)
public native String intern();
public String repeat(int count) {


[JavaLangAccess]    static String newStringUTF8NoRepl(byte[] bytes, int offset, int length)
[JavaLangAccess]    static String newStringNoRepl(byte[] src, Charset cs) throws CharacterCodingException
[JavaLangAccess]    static byte[] getBytesUTF8NoRepl(String s) {
[JavaLangAccess]    static byte[] getBytesNoRepl(String s, Charset cs) throws CharacterCodingException {
[JavaLangAccess]    static int decodeASCII(byte[] sa, int sp, char[] da, int dp, int len) {
[AbstractStringBuilder]    static int indexOf(byte[] src, byte srcCoder, int srcCount, String tgtStr, int fromIndex) {
[AbstractStringBuilder]    static int lastIndexOf(byte[] src, byte srcCoder, int srcCount, String tgtStr, int fromIndex) {
[JavaLangAccess]    static String join(String prefix, String suffix, String delimiter, String[] elements, int size) {
[AbstractStringBuilder,StringConcatHelper]    void getBytes(byte[] dst, int dstBegin, byte coder) {
[AbstractStringBuilder]    void getBytes(byte[] dst, int srcPos, int dstBegin, byte coder, int length) {
[StringBuffer,StringBuilder]    String(AbstractStringBuilder asb, Void sig) {
[Integer, Long, ..., StringConcatHelper, StringUtf8, ...]    String(byte[] value, byte coder)
[AbstractStringBuilder,StringConcatHelper]    byte coder() {
[AbstractStringBuilder]    byte[] value() {
[AbstractStringBuilder]    boolean isLatin1() {


// уже есть у Вали
[AbstractStringBuilder, StringUtf8, ...]    static void checkIndex(int index, int length) {
[AbstractStringBuilder, StringUtf8, ...]    static void checkOffset(int offset, int length) {
[AbstractStringBuilder, StringUtf8, ...]    static void checkBoundsOffCount(int offset, int count, int length) {
[AbstractStringBuilder, StringUtf8, ...]    static void checkBoundsBeginEnd(int begin, int end, int length) {
[Character.toString]    static String valueOfCodePoint(int codePoint) {

 */