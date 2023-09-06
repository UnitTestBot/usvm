package org.usvm.machine

import io.ksmt.utils.asExpr
import io.ksmt.utils.uncheckedCast
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.byte
import org.jacodb.api.ext.char
import org.jacodb.api.ext.double
import org.jacodb.api.ext.float
import org.jacodb.api.ext.int
import org.jacodb.api.ext.long
import org.jacodb.api.ext.objectClass
import org.jacodb.api.ext.short
import org.jacodb.api.ext.void
import org.usvm.UBv32Sort
import org.usvm.UBvSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UFpSort
import org.usvm.USymbolicHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.uctx

class JcMethodApproximationResolver(
    private val ctx: JcContext,
    private val scope: JcStepScope,
    private val applicationGraph: JcApplicationGraph,
    private val exprResolver: JcExprResolver
) {
    fun approximate(callJcInst: UJcMethodCall): Boolean {
        if (skipMethodIfThrowable(callJcInst)) {
            return true
        }

        if (callJcInst.method.isStatic) {
            return approximateStaticMethod(callJcInst)
        }

        return approximateRegularMethod(callJcInst)
    }

    private fun approximateRegularMethod(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        if (method.enclosingClass == ctx.cp.objectClass) {
            if (approximateObjectVirtualMethod(methodCall)) return true
        }

        if (method.enclosingClass == ctx.classType.jcClass) {
            if (approximateClassVirtualMethod(methodCall)) return true
        }

        if (method.enclosingClass.name == "jdk.internal.misc.Unsafe") {
            if (approximateUnsafeVirtualMethod(methodCall)) return true
        }

        return approximateEmptyNativeMethod(methodCall)
    }

    private fun approximateStaticMethod(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        if (method.enclosingClass == ctx.classType.jcClass) {
            if (approximateClassStaticMethod(methodCall)) return true
        }

        if (method.enclosingClass.name == "java.lang.System") {
            if (approximateSystemStaticMethod(methodCall)) return true
        }

        if (method.enclosingClass.name == "java.lang.StringUTF16") {
            if (approximateStringUtf16StaticMethod(methodCall)) return true
        }

        if (method.enclosingClass.name == "java.lang.Float") {
            if (approximateFloatStaticMethod(methodCall)) return true
        }

        if (method.enclosingClass.name == "java.lang.Double") {
            if (approximateDoubleStaticMethod(methodCall)) return true
        }

        return approximateEmptyNativeMethod(methodCall)
    }

    private fun approximateEmptyNativeMethod(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        if (method.isNative && method.hasVoidReturnType() && method.parameters.isEmpty()) {
            if (method.enclosingClass.declaration.location.isRuntime) {
                /**
                 * Native methods in the standard library with no return value and no
                 * arguments have no visible effect and can be skipped
                 * */
                scope.doWithState {
                    skipMethodInvocationWithValue(methodCall, ctx.voidValue)
                }
                return true
            }
        }

        return false
    }

    private fun approximateClassStaticMethod(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        /**
         * Approximate retrieval of class instance for primitives.
         * */
        if (method.name == "getPrimitiveClass") {
            val classNameRef = arguments.single()

            val predefinedTypeNames = ctx.primitiveTypes.associateBy {
                exprResolver.resolveStringConstant(it.typeName)
            }

            val primitive = predefinedTypeNames[classNameRef] ?: return false

            val classRef = exprResolver.resolveClassRef(primitive)

            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, classRef)
            }
            return true
        }

        return false
    }

    private fun approximateClassVirtualMethod(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        /**
         * Approximate assertions enabled check.
         * It is correct to enable assertions during analysis.
         * */
        if (method.name == "desiredAssertionStatus") {
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, ctx.trueExpr)
            }
            return true
        }

        return false
    }

    private fun approximateObjectVirtualMethod(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        if (method.name == "getClass") {
            val instance = arguments.first().asExpr(ctx.addressSort)

            // Type constraints can't provide types for other refs
            if (instance !is UConcreteHeapRef && instance !is USymbolicHeapRef) {
                return false
            }

            val possibleTypes = scope.calcOnState { memory.typeStreamOf(instance).take(2) }

            /**
             * Since getClass is a virtual method, typeStream has been constrained
             * to a single concrete type by the [JcInterpreter.resolveVirtualInvoke]
             * */
            val type = possibleTypes.singleOrNull() ?: return false

            val result = exprResolver.resolveClassRef(type)
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        return false
    }

    private fun approximateUnsafeVirtualMethod(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        // Array offset is usually the same on various JVM
        if (method.name == "arrayBaseOffset0") {
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, ctx.mkBv(16, ctx.integerSort))
            }
            return true
        }

        if (method.name == "arrayIndexScale0") {
            val primitiveArrayScale = mapOf(
                ctx.cp.boolean to 1,
                ctx.cp.byte to Byte.SIZE_BYTES,
                ctx.cp.short to Short.SIZE_BYTES,
                ctx.cp.int to Int.SIZE_BYTES,
                ctx.cp.long to Long.SIZE_BYTES,
                ctx.cp.char to Char.SIZE_BYTES,
                ctx.cp.float to Float.SIZE_BYTES,
                ctx.cp.double to Double.SIZE_BYTES,
            )

            val primitiveArrayRefScale = primitiveArrayScale.mapKeys { (type, _) ->
                exprResolver.resolveClassRef(ctx.cp.arrayTypeOf(type))
            }

            val arrayTypeRef = arguments.last().asExpr(ctx.addressSort)

            val result = primitiveArrayRefScale.entries.fold(
                // All non-primitive (object) arrays usually have 4 bytes scale on various JVM
                ctx.mkBv(4, ctx.integerSort) as UExpr<UBv32Sort>
            ) { res, (typeRef, scale) ->
                ctx.mkIte(ctx.mkHeapRefEq(arrayTypeRef, typeRef), ctx.mkBv(scale, ctx.integerSort), res)
            }

            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        return false
    }

    private fun approximateSystemStaticMethod(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        if (method.name == "arraycopy") {
            // Object src, int srcPos, Object dest, int destPos, int length
            val (srcRef, srcPos, dstRef, dstPos, length) = arguments
            with(srcRef.uctx) {
                exprResolver.resolveArrayCopy(
                    methodCall = methodCall,
                    srcRef = srcRef.asExpr(addressSort),
                    srcPos = srcPos.asExpr(sizeSort),
                    dstRef = dstRef.asExpr(addressSort),
                    dstPos = dstPos.asExpr(sizeSort),
                    length = length.asExpr(sizeSort),
                )
            }
            return true
        }

        return false
    }

    private fun approximateStringUtf16StaticMethod(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        // Use common property value as approximation
        if (method.name == "isBigEndian") {
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, ctx.falseExpr)
            }
            return true
        }
        return false
    }

    private fun approximateFloatStaticMethod(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        if (method.name == "floatToRawIntBits") {
            val value = arguments.single().asExpr(ctx.floatSort)
            val result = ctx.mkFpToIEEEBvExpr(value).asExpr(ctx.integerSort)
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        if (method.name == "intBitsToFloat") {
            val value = arguments.single().asExpr(ctx.integerSort)
            val result = mkFpFromBits(ctx.floatSort, value)
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        return false
    }

    private fun approximateDoubleStaticMethod(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        if (method.name == "doubleToRawLongBits") {
            val value = arguments.single().asExpr(ctx.doubleSort)
            val result = ctx.mkFpToIEEEBvExpr(value).asExpr(ctx.longSort)
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        if (method.name == "longBitsToDouble") {
            val value = arguments.single().asExpr(ctx.longSort)
            val result = mkFpFromBits(ctx.doubleSort, value)
            scope.doWithState {
                skipMethodInvocationWithValue(methodCall, result)
            }
            return true
        }

        return false
    }

    private fun skipMethodIfThrowable(methodCall: UJcMethodCall): Boolean = with(methodCall) {
        if (method.enclosingClass.name == "java.lang.Throwable") {
            scope.doWithState {
                val nextStmt = applicationGraph.successors(methodCall.returnSite).single()
                newStmt(nextStmt)
            }
            return true
        }
        return false
    }

    private fun <Fp : UFpSort> mkFpFromBits(sort: Fp, bits: UExpr<out UBvSort>): UExpr<Fp> = with(ctx) {
        val exponentBits = sort.exponentBits.toInt()
        val size = bits.sort.sizeBits.toInt()

        val sign = mkBvExtractExpr(size - 1, size - 1, bits)
        val exponent = mkBvExtractExpr(size - 2, size - exponentBits - 1, bits)
        val significand = mkBvExtractExpr(size - exponentBits - 2, 0, bits)

        mkFpFromBvExpr(sign.uncheckedCast(), exponent, significand)
    }

    private fun JcMethod.hasVoidReturnType(): Boolean =
        returnType.typeName == ctx.cp.void.typeName
}
