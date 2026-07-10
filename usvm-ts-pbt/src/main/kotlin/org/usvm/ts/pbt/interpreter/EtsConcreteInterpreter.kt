package org.usvm.ts.pbt.interpreter

import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAndExpr
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBitAndExpr
import org.jacodb.ets.model.EtsBitNotExpr
import org.jacodb.ets.model.EtsBitOrExpr
import org.jacodb.ets.model.EtsBitXorExpr
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsExpExpr
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsGlobalRef
import org.jacodb.ets.model.EtsGtEqExpr
import org.jacodb.ets.model.EtsGtExpr
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsInExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsLValue
import org.jacodb.ets.model.EtsLeftShiftExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsLtEqExpr
import org.jacodb.ets.model.EtsLtExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMulExpr
import org.jacodb.ets.model.EtsNegExpr
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsNotEqExpr
import org.jacodb.ets.model.EtsNotExpr
import org.jacodb.ets.model.EtsNullConstant
import org.jacodb.ets.model.EtsNullishCoalescingExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsOrExpr
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsPostDecExpr
import org.jacodb.ets.model.EtsPostIncExpr
import org.jacodb.ets.model.EtsPreDecExpr
import org.jacodb.ets.model.EtsPreIncExpr
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRemExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsRightShiftExpr
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStrictEqExpr
import org.jacodb.ets.model.EtsStrictNotEqExpr
import org.jacodb.ets.model.EtsStringConstant
import org.jacodb.ets.model.EtsSubExpr
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsTypeOfExpr
import org.jacodb.ets.model.EtsUnaryPlusExpr
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUndefinedConstant
import org.jacodb.ets.model.EtsUnsignedRightShiftExpr
import org.jacodb.ets.model.EtsVoidExpr
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import kotlin.math.floor

/**
 * A concrete (non-symbolic) interpreter for EtsIR.
 *
 * Executes an [EtsMethod] CFG with concrete [VValue] inputs — the execution
 * vehicle of the PBT phase of the hybrid analysis. Anything the interpreter
 * does not model is surfaced as [ExecutionResult.Unsupported], never as a
 * silently wrong value.
 */
class EtsConcreteInterpreter(
    private val scene: EtsScene,
    private val limits: ExecutionLimits = ExecutionLimits(),
) {
    private val resolver = CallResolver(scene)

    fun execute(
        method: EtsMethod,
        thisValue: VValue = VUndefined,
        args: List<VValue> = emptyList(),
        listener: ExecutionListener = ExecutionListener.NONE,
    ): ExecutionResult {
        val execution = Execution(listener)
        return try {
            ExecutionResult.Returned(execution.runMethod(method, thisValue, args))
        } catch (e: JsThrowSignal) {
            ExecutionResult.Threw(e.value)
        } catch (e: UnsupportedFeatureSignal) {
            ExecutionResult.Unsupported(e.reason)
        } catch (e: BudgetExceededSignal) {
            ExecutionResult.Diverged(e.reason)
        } catch (e: StackOverflowError) {
            ExecutionResult.Diverged("JVM stack overflow (deep recursion)")
        }
    }

    private class Frame(
        val method: EtsMethod,
        val thisValue: VValue,
        val args: List<VValue>,
        val locals: MutableMap<String, VValue> = mutableMapOf(),
    )

    private inner class Execution(val listener: ExecutionListener) {
        var steps: Long = 0
        var callDepth: Int = 0
        val statics: MutableMap<String, MutableMap<String, VValue>> = mutableMapOf()

        fun runMethod(method: EtsMethod, thisValue: VValue, args: List<VValue>): VValue {
            if (callDepth >= limits.maxCallDepth) {
                throw BudgetExceededSignal("max call depth ${limits.maxCallDepth} exceeded at ${method.name}")
            }
            val stmts = method.cfg.stmts
            if (stmts.isEmpty()) {
                throw UnsupportedFeatureSignal("method without body: ${method.signature}")
            }

            callDepth++
            listener.onMethodEnter(method, thisValue, args)
            try {
                val frame = Frame(method, thisValue, args)
                var pc: EtsStmt? = stmts.first()

                while (pc != null) {
                    if (++steps > limits.maxSteps) {
                        throw BudgetExceededSignal("step budget ${limits.maxSteps} exceeded")
                    }
                    listener.onStmt(pc)

                    when (val stmt = pc) {
                        is EtsNopStmt -> pc = next(method, stmt)

                        is EtsAssignStmt -> {
                            val value = eval(stmt.rhv, frame)
                            assign(stmt.lhv, value, frame)
                            pc = next(method, stmt)
                        }

                        is EtsCallStmt -> {
                            evalCall(stmt.expr, frame)
                            pc = next(method, stmt)
                        }

                        is EtsIfStmt -> {
                            val condition = JsSemantics.truthy(eval(stmt.condition, frame))
                            val successors = method.cfg.successors(stmt).toList()
                            if (successors.size != 2) {
                                throw UnsupportedFeatureSignal(
                                    "if-stmt with ${successors.size} successors: $stmt"
                                )
                            }
                            // Ordered: first = true branch, second = false branch
                            val taken = if (condition) successors[0] else successors[1]
                            listener.onBranch(stmt, taken, condition)
                            pc = taken
                        }

                        is EtsReturnStmt -> {
                            val result = stmt.returnValue?.let { eval(it, frame) } ?: VUndefined
                            listener.onMethodExit(method, result)
                            return result
                        }

                        is EtsThrowStmt -> {
                            throw JsThrowSignal(eval(stmt.exception, frame))
                        }

                        else -> throw UnsupportedFeatureSignal("statement kind: ${stmt::class.simpleName} ($stmt)")
                    }
                }

                // CFG ended without an explicit return
                listener.onMethodExit(method, VUndefined)
                return VUndefined
            } finally {
                callDepth--
            }
        }

        private fun next(method: EtsMethod, stmt: EtsStmt): EtsStmt? =
            method.cfg.successors(stmt).firstOrNull()

        // ---------------------------------------------------------------
        // Expression evaluation
        // ---------------------------------------------------------------

        fun eval(e: EtsEntity, frame: Frame): VValue = when (e) {
            // Immediates
            is EtsLocal -> frame.locals[e.name]
                ?: frame.args.getOrNull(parameterIndexOfLocal(frame, e.name) ?: -1)
                ?: functionValueOf(e)
                ?: VUndefined

            is EtsNumberConstant -> VNumber(e.value)
            is EtsStringConstant -> VString(e.value)
            is EtsBooleanConstant -> VBool(e.value)
            EtsNullConstant -> VNull
            EtsUndefinedConstant -> VUndefined

            // Refs
            is EtsThis -> frame.thisValue
            is EtsParameterRef -> frame.args.getOrElse(e.index) { VUndefined }
            is EtsArrayAccess -> readArray(eval(e.array, frame), eval(e.index, frame))
            is EtsInstanceFieldRef -> readField(eval(e.instance, frame), e.field.name)
            is EtsStaticFieldRef -> readStaticField(e)
            is EtsGlobalRef ->
                if (e.name in Intrinsics.NAMESPACES) VNamespace(e.name)
                else throw UnsupportedFeatureSignal("global ref: ${e.name}")

            // Allocation
            is EtsNewExpr -> newObject(e)
            is EtsNewArrayExpr -> {
                val size = JsSemantics.toNumber(eval(e.size, frame))
                if (size.isNaN() || size < 0 || size != floor(size)) {
                    throw JsThrowSignal(VString("RangeError: Invalid array length"))
                }
                VArray(MutableList(size.toInt()) { VUndefined })
            }

            // Unary
            is EtsNotExpr -> VBool(!JsSemantics.truthy(eval(e.arg, frame)))
            is EtsNegExpr -> VNumber(-JsSemantics.toNumber(eval(e.arg, frame)))
            is EtsUnaryPlusExpr -> VNumber(JsSemantics.toNumber(eval(e.arg, frame)))
            is EtsBitNotExpr -> VNumber(JsSemantics.toInt32(eval(e.arg, frame)).inv().toDouble())
            is EtsTypeOfExpr -> VString(JsSemantics.typeOf(eval(e.arg, frame)))
            is EtsVoidExpr -> {
                eval(e.arg, frame)
                VUndefined
            }

            is EtsPreIncExpr -> incDec(e.arg, frame, delta = 1.0, returnNew = true)
            is EtsPreDecExpr -> incDec(e.arg, frame, delta = -1.0, returnNew = true)
            is EtsPostIncExpr -> incDec(e.arg, frame, delta = 1.0, returnNew = false)
            is EtsPostDecExpr -> incDec(e.arg, frame, delta = -1.0, returnNew = false)

            is EtsCastExpr -> eval(e.arg, frame)
            is EtsInstanceOfExpr -> VBool(instanceOf(eval(e.arg, frame), e))

            // Binary: arithmetic
            is EtsAddExpr -> JsSemantics.add(eval(e.left, frame), eval(e.right, frame))
            is EtsSubExpr -> numeric(e.left, e.right, frame) { a, b -> a - b }
            is EtsMulExpr -> numeric(e.left, e.right, frame) { a, b -> a * b }
            is EtsDivExpr -> numeric(e.left, e.right, frame) { a, b -> a / b }
            is EtsRemExpr -> numeric(e.left, e.right, frame) { a, b -> a % b }
            is EtsExpExpr -> numeric(e.left, e.right, frame) { a, b -> Math.pow(a, b) }

            // Binary: bitwise / shifts
            is EtsBitAndExpr -> int32(e.left, e.right, frame) { a, b -> a and b }
            is EtsBitOrExpr -> int32(e.left, e.right, frame) { a, b -> a or b }
            is EtsBitXorExpr -> int32(e.left, e.right, frame) { a, b -> a xor b }
            is EtsLeftShiftExpr -> int32(e.left, e.right, frame) { a, b -> a shl (b and 31) }
            is EtsRightShiftExpr -> int32(e.left, e.right, frame) { a, b -> a shr (b and 31) }
            is EtsUnsignedRightShiftExpr -> {
                val a = JsSemantics.toUint32(eval(e.left, frame))
                val b = JsSemantics.toInt32(eval(e.right, frame)) and 31
                VNumber((a ushr b).toDouble())
            }

            // Binary: relational.
            //
            // NOTE on the truthiness idiom: front ends lower `if (x)` to
            // `x != 0` (ArkAnalyzer) / `x != false` (ts-frontend) — byte-identical
            // to a genuine loose comparison in the IR, although the two readings
            // diverge (`[] != 0` and `NaN != 0` vs the truthiness of `[]`/`NaN`).
            // We follow the idiom contract: `!=` against literal zero/false is
            // ToBoolean for ALL operand kinds. The idiom only ever uses `!=`
            // (negated tests swap branch successors), so `==` keeps the literal
            // loose-equality semantics. The engine mirrors this contract.
            is EtsEqExpr ->
                VBool(JsSemantics.looseEq(eval(e.left, frame), eval(e.right, frame)))

            is EtsNotEqExpr ->
                if (isZeroOrFalseConstant(e.right)) {
                    VBool(JsSemantics.truthy(eval(e.left, frame)))
                } else {
                    VBool(!JsSemantics.looseEq(eval(e.left, frame), eval(e.right, frame)))
                }
            is EtsStrictEqExpr -> VBool(JsSemantics.strictEq(eval(e.left, frame), eval(e.right, frame)))
            is EtsStrictNotEqExpr -> VBool(!JsSemantics.strictEq(eval(e.left, frame), eval(e.right, frame)))
            is EtsLtExpr -> VBool(JsSemantics.lt(eval(e.left, frame), eval(e.right, frame)))
            is EtsLtEqExpr -> VBool(JsSemantics.le(eval(e.left, frame), eval(e.right, frame)))
            is EtsGtExpr -> VBool(JsSemantics.gt(eval(e.left, frame), eval(e.right, frame)))
            is EtsGtEqExpr -> VBool(JsSemantics.ge(eval(e.left, frame), eval(e.right, frame)))
            is EtsInExpr -> VBool(JsSemantics.inOp(eval(e.left, frame), eval(e.right, frame)))

            // Binary: logical (operands are pre-flattened locals in 3AC, eager evaluation is safe)
            is EtsAndExpr -> {
                val l = eval(e.left, frame)
                if (JsSemantics.truthy(l)) eval(e.right, frame) else l
            }

            is EtsOrExpr -> {
                val l = eval(e.left, frame)
                if (JsSemantics.truthy(l)) l else eval(e.right, frame)
            }

            is EtsNullishCoalescingExpr -> {
                val l = eval(e.left, frame)
                if (l == VNull || l == VUndefined) eval(e.right, frame) else l
            }

            // Calls
            is EtsCallExpr -> evalCall(e, frame)

            else -> throw UnsupportedFeatureSignal("expression kind: ${e::class.simpleName} ($e)")
        }

        /** A local that was never assigned may actually be a named parameter. */
        private fun parameterIndexOfLocal(frame: Frame, name: String): Int? =
            frame.method.parameters.firstOrNull { it.name == name }?.index

        private fun isZeroOrFalseConstant(e: EtsEntity): Boolean =
            (e is EtsNumberConstant && e.value == 0.0) ||
                (e is EtsBooleanConstant && !e.value)

        /**
         * A local of a function type that was never assigned is a function
         * *literal reference*: its type signature names the lowered method
         * (e.g. `factorial := %AM0$%dflt` in the file initializer).
         */
        private fun functionValueOf(e: EtsLocal): VValue? {
            val fnType = e.type as? EtsFunctionType ?: return null
            val method = resolver.methodByFunctionType(fnType)
                ?: resolver.resolveFunctionPointer(fnType.signature)
                ?: resolver.functionAliasFor(e.name)
                ?: return null
            return VFunction(method)
        }

        private inline fun numeric(
            left: EtsEntity,
            right: EtsEntity,
            frame: Frame,
            op: (Double, Double) -> Double,
        ): VNumber = VNumber(op(JsSemantics.toNumber(eval(left, frame)), JsSemantics.toNumber(eval(right, frame))))

        private inline fun int32(
            left: EtsEntity,
            right: EtsEntity,
            frame: Frame,
            op: (Int, Int) -> Int,
        ): VNumber = VNumber(op(JsSemantics.toInt32(eval(left, frame)), JsSemantics.toInt32(eval(right, frame))).toDouble())

        private fun incDec(arg: EtsEntity, frame: Frame, delta: Double, returnNew: Boolean): VValue {
            if (arg !is EtsLocal) {
                throw UnsupportedFeatureSignal("inc/dec on non-local: $arg")
            }
            val old = JsSemantics.toNumber(eval(arg, frame))
            val new = old + delta
            frame.locals[arg.name] = VNumber(new)
            return VNumber(if (returnNew) new else old)
        }

        private fun instanceOf(v: VValue, e: EtsInstanceOfExpr): Boolean {
            val typeName = when (val t = e.checkType) {
                is EtsClassType -> t.signature.name
                is EtsUnclearRefType -> t.name
                else -> return false
            }
            return when (v) {
                is VArray -> typeName == "Array"
                is VMap -> typeName == "Map"
                is VSet -> typeName == "Set"
                is VFunction -> typeName == "Function"
                is VObject -> {
                    var cls = v.cls
                    val visited = mutableSetOf<String>()
                    while (cls != null && visited.add(cls.name)) {
                        if (cls.name == typeName) return true
                        cls = cls.superClass?.let { resolver.classBySignature(it) }
                    }
                    typeName == "Object"
                }

                else -> false
            }
        }

        // ---------------------------------------------------------------
        // Heap access
        // ---------------------------------------------------------------

        private fun readArray(array: VValue, index: VValue): VValue = when (array) {
            is VArray -> {
                val i = JsSemantics.toNumber(index)
                if (i == floor(i) && i >= 0 && i < array.elements.size) array.elements[i.toInt()] else VUndefined
            }

            is VString -> {
                val i = JsSemantics.toNumber(index)
                if (i == floor(i) && i >= 0 && i < array.value.length) VString(array.value[i.toInt()].toString())
                else VUndefined
            }

            VNull, VUndefined -> throw typeError("cannot read index of ${JsSemantics.toStringJs(array)}")

            is VObject -> array.fields[JsSemantics.toStringJs(index)] ?: VUndefined

            else -> VUndefined
        }

        private fun readField(instance: VValue, name: String): VValue = when (instance) {
            is VObject -> instance.fields[name] ?: VUndefined
            is VArray -> if (name == "length") VNumber(instance.elements.size.toDouble()) else VUndefined
            is VString -> if (name == "length") VNumber(instance.value.length.toDouble()) else VUndefined
            is VMap -> if (name == "size") VNumber(instance.entries.size.toDouble()) else VUndefined
            is VSet -> if (name == "size") VNumber(instance.elements.size.toDouble()) else VUndefined
            is VNamespace -> Intrinsics.namespaceField(instance.name, name)
                ?: throw UnsupportedFeatureSignal("namespace field: ${instance.name}.$name")

            VNull, VUndefined ->
                throw typeError("cannot read property '$name' of ${JsSemantics.toStringJs(instance)}")

            else -> VUndefined
        }

        private fun readStaticField(ref: EtsStaticFieldRef): VValue {
            val className = ref.field.enclosingClass.name
            Intrinsics.namespaceField(className, ref.field.name)?.let { return it }
            return statics[className]?.get(ref.field.name) ?: VUndefined
        }

        private fun newObject(e: EtsNewExpr): VValue {
            val typeName = when (val t = e.type) {
                is EtsClassType -> t.signature.name
                is EtsUnclearRefType -> t.name
                else -> null
            }
            val cls = when (val t = e.type) {
                is EtsClassType -> resolver.classBySignature(t.signature)
                is EtsUnclearRefType -> resolver.classByName(t.name)
                else -> null
            }
            if (cls == null) {
                // Built-in containers are not scene classes
                when (typeName) {
                    "Array" -> return VArray()
                    "Map" -> return VMap()
                    "Set" -> return VSet()
                }
            }
            return VObject(cls)
        }

        private fun assign(lhv: EtsLValue, value: VValue, frame: Frame) {
            when (lhv) {
                is EtsLocal -> frame.locals[lhv.name] = value

                is EtsArrayAccess -> {
                    val target = eval(lhv.array, frame)
                    val i = JsSemantics.toNumber(eval(lhv.index, frame))
                    when (target) {
                        is VArray -> {
                            if (i != floor(i) || i < 0) return // JS silently allows sparse/weird keys; skip
                            val idx = i.toInt()
                            while (target.elements.size <= idx) target.elements.add(VUndefined)
                            target.elements[idx] = value
                        }

                        is VObject -> target.fields[JsSemantics.numberToString(i)] = value

                        VNull, VUndefined -> throw typeError("cannot set index of ${JsSemantics.toStringJs(target)}")

                        else -> Unit // writes to primitives are silently ignored in JS
                    }
                }

                is EtsInstanceFieldRef -> {
                    when (val target = eval(lhv.instance, frame)) {
                        is VObject -> target.fields[lhv.field.name] = value

                        is VArray -> if (lhv.field.name == "length") {
                            val n = JsSemantics.toNumber(value).toInt()
                            while (target.elements.size > n) target.elements.removeAt(target.elements.size - 1)
                            while (target.elements.size < n) target.elements.add(VUndefined)
                        }

                        VNull, VUndefined ->
                            throw typeError("cannot set property '${lhv.field.name}' of ${JsSemantics.toStringJs(target)}")

                        else -> Unit
                    }
                }

                is EtsStaticFieldRef -> {
                    statics.getOrPut(lhv.field.enclosingClass.name) { mutableMapOf() }[lhv.field.name] = value
                }

                else -> throw UnsupportedFeatureSignal("assignment target: ${lhv::class.simpleName} ($lhv)")
            }
        }

        // ---------------------------------------------------------------
        // Calls
        // ---------------------------------------------------------------

        fun evalCall(expr: EtsCallExpr, frame: Frame): VValue {
            val args = expr.args.map { eval(it, frame) }

            return when (expr) {
                is EtsInstanceCallExpr -> {
                    val receiver = eval(expr.instance, frame)
                    val name = expr.callee.name

                    if (receiver is VNamespace) {
                        return Intrinsics.callNamespace(receiver.name, name, args)
                            ?: throw UnsupportedFeatureSignal("intrinsic: ${receiver.name}.$name")
                    }

                    // Namespace receiver referenced by its bare local name (Math, console, ...)
                    if (receiver == VUndefined && expr.instance.name in Intrinsics.NAMESPACES) {
                        return Intrinsics.callNamespace(expr.instance.name, name, args)
                            ?: throw UnsupportedFeatureSignal("intrinsic: ${expr.instance.name}.$name")
                    }

                    if (receiver == VNull || receiver == VUndefined) {
                        throw typeError("cannot call '$name' of ${JsSemantics.toStringJs(receiver)}")
                    }

                    if (receiver is VObject && receiver.cls != null) {
                        val callee = resolver.resolveInstanceMethod(receiver.cls, name)
                        if (callee != null) {
                            return runMethod(callee, receiver, padArgs(callee, args))
                        }
                    }

                    // A function value stored in an object field: `this.#cmp(a, b)`
                    if (receiver is VObject) {
                        val fieldFn = receiver.fields[name]
                        if (fieldFn is VFunction) {
                            return runMethod(fieldFn.method, receiver, padArgs(fieldFn.method, args))
                        }
                    }

                    // Constructors of classes outside the scene.
                    if (name == CONSTRUCTOR_NAME) {
                        when (receiver) {
                            is VObject -> {
                                // e.g. `new Error(msg)`: model as record initialization
                                args.firstOrNull()?.let { receiver.fields["message"] = it }
                                return receiver
                            }

                            is VArray -> {
                                // `new Array(n)` / `new Array(a, b, ...)`
                                if (args.size == 1 && args[0] is VNumber) {
                                    val n = (args[0] as VNumber).value
                                    if (n == floor(n) && n >= 0) {
                                        receiver.elements.clear()
                                        repeat(n.toInt()) { receiver.elements.add(VUndefined) }
                                    } else {
                                        throw JsThrowSignal(VString("RangeError: Invalid array length"))
                                    }
                                } else {
                                    receiver.elements.clear()
                                    receiver.elements.addAll(args)
                                }
                                return receiver
                            }

                            is VMap, is VSet -> {
                                if (args.isEmpty() || args[0] == VUndefined || args[0] == VNull) return receiver
                                if (receiver is VSet && args[0] is VArray) {
                                    (args[0] as VArray).elements.forEach { receiver.elements.add(it) }
                                    return receiver
                                }
                                throw UnsupportedFeatureSignal("iterable constructor argument for Map/Set")
                            }

                            else -> Unit
                        }
                    }

                    Intrinsics.callInstance(receiver, name, args, ::invokeFunction)
                        ?: throw UnsupportedFeatureSignal(
                            "instance method: $name on ${receiver::class.simpleName}"
                        )
                }

                is EtsStaticCallExpr -> {
                    val className = expr.callee.enclosingClass.name

                    Intrinsics.callNamespace(className, expr.callee.name, args)?.let { return it }
                    Intrinsics.callConversion(expr.callee.name, args)?.let { return it }

                    val callee = resolver.resolveStaticMethod(expr.callee)
                        ?: throw UnsupportedFeatureSignal("static callee not found: ${expr.callee}")
                    runMethod(callee, VUndefined, padArgs(callee, args))
                }

                is EtsPtrCallExpr -> {
                    // Prefer the dynamic function value held by the pointer local
                    val ptrValue = eval(expr.ptr, frame)
                    if (ptrValue is VFunction) {
                        runMethod(ptrValue.method, ptrValue.thisValue, padArgs(ptrValue.method, args))
                    } else {
                        val callee = resolver.resolveFunctionPointer(expr.callee)
                        if (callee != null) {
                            runMethod(callee, frame.thisValue, padArgs(callee, args))
                        } else {
                            Intrinsics.callConversion(expr.callee.name, args)
                                ?: throw UnsupportedFeatureSignal("ptr call: ${expr.callee.name}")
                        }
                    }
                }

                else -> throw UnsupportedFeatureSignal("call kind: ${expr::class.simpleName}")
            }
        }

        private fun padArgs(callee: EtsMethod, args: List<VValue>): List<VValue> {
            if (args.size >= callee.parameters.size) return args
            return args + List(callee.parameters.size - args.size) { VUndefined }
        }

        /** Host callback for higher-order intrinsics (`arr.map(f)`, `map.forEach(f)`, ...). */
        private fun invokeFunction(fn: VFunction, args: List<VValue>): VValue =
            runMethod(fn.method, fn.thisValue, padArgs(fn.method, args))
    }
}
