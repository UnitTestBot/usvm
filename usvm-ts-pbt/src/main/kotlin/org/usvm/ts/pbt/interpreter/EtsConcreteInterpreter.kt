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
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import java.util.IdentityHashMap
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

    private sealed interface NativeCallable {
        data class IteratorFactory(val receiver: VValue) : NativeCallable
        data class IteratorMethod(val iterator: VObject, val method: String) : NativeCallable
    }

    private data class IteratorState(
        val receiver: VValue,
        val kind: String,
        var index: Int = 0,
        var finished: Boolean = false,
    )

    private data class ObjectProperty(val value: VValue)

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
        val temporaryCallReceivers: MutableMap<String, VValue> = mutableMapOf(),
    )

    private inner class Execution(val listener: ExecutionListener) {
        var steps: Long = 0
        var callDepth: Int = 0
        val statics: MutableMap<String, MutableMap<String, VValue>> = mutableMapOf()
        val nativeCallables: IdentityHashMap<VObject, NativeCallable> = IdentityHashMap()
        val nativeFunctionCache: IdentityHashMap<VValue, MutableMap<String, VNativeFunction>> = IdentityHashMap()
        val iterators: IdentityHashMap<VObject, IteratorState> = IdentityHashMap()
        val methodFunctionCache: IdentityHashMap<EtsMethod, VFunction> = IdentityHashMap()

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
                            preserveTemporaryCallReference(stmt, value, frame)
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

        private fun preserveTemporaryCallReference(
            stmt: EtsAssignStmt,
            value: VValue,
            frame: Frame,
        ) {
            val target = stmt.lhv as? EtsLocal ?: return
            frame.temporaryCallReceivers.remove(target.name)
            if (!target.name.startsWith("%") || value !is VFunction ||
                value.thisMode != VFunctionThisMode.DYNAMIC
            ) {
                return
            }
            val receiver = when (val source = stmt.rhv) {
                is EtsInstanceFieldRef -> eval(source.instance, frame)
                is EtsArrayAccess -> eval(source.array, frame)
                is EtsLocal -> frame.temporaryCallReceivers[source.name] ?: return
                else -> return
            }
            frame.temporaryCallReceivers[target.name] = receiver
        }

        // ---------------------------------------------------------------
        // Expression evaluation
        // ---------------------------------------------------------------

        fun eval(e: EtsEntity, frame: Frame): VValue = when (e) {
            // Immediates
            is EtsLocal -> evalLocal(e, frame)

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
            is EtsGlobalRef -> if (e.name in Intrinsics.NAMESPACES) {
                VNamespace(e.name)
            } else {
                throw UnsupportedFeatureSignal("global ref: ${e.name}")
            }

            // Allocation
            is EtsNewExpr -> newObject(e)
            is EtsNewArrayExpr -> {
                val size = checkedConcreteArrayLength(
                    JsSemantics.toNumber(eval(e.size, frame)),
                    limits.maxArrayLength,
                )
                VArray(MutableList(size) { VUndefined })
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
            is EtsInExpr -> VBool(propertyIn(eval(e.left, frame), eval(e.right, frame)))

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

        private fun evalLocal(local: EtsLocal, frame: Frame): VValue {
            frame.locals[local.name]?.let { return it }
            parameterIndexOfLocal(frame, local.name)?.let { parameterIndex ->
                return frame.args.getOrElse(parameterIndex) { VUndefined }
            }
            if (local.name == "arguments") return VArray(frame.args.toMutableList())
            if (local.name in Intrinsics.NAMESPACES) return VNamespace(local.name)
            resolver.modulePathOf(local, frame.method.signature.enclosingClass.file.fileName)
                ?.let { return VNamespace("module:$it") }
            return functionValueOf(local, frame.method) ?: VUndefined
        }

        private fun isZeroOrFalseConstant(e: EtsEntity): Boolean =
            (e is EtsNumberConstant && e.value == 0.0) ||
                (e is EtsBooleanConstant && !e.value)

        /**
         * A local of a function type that was never assigned is a function
         * *literal reference*: its type signature names the lowered method
         * (e.g. `factorial := %AM0$%dflt` in the file initializer).
         */
        private fun functionValueOf(e: EtsLocal, enclosingMethod: EtsMethod): VValue? {
            val fnType = e.type as? EtsFunctionType ?: return null
            val method = resolver.methodByFunctionType(fnType)
                ?: resolver.resolveFunctionPointer(fnType.signature)
                ?: resolver.functionAliasFor(e.name, enclosingMethod.signature.enclosingClass.file.fileName)
                ?: return null
            return VFunction(method)
        }

        private inline fun numeric(
            left: EtsEntity,
            right: EtsEntity,
            frame: Frame,
            op: (Double, Double) -> Double,
        ): VNumber = VNumber(
            op(
                JsSemantics.toNumber(eval(left, frame)),
                JsSemantics.toNumber(eval(right, frame)),
            ),
        )

        private inline fun int32(
            left: EtsEntity,
            right: EtsEntity,
            frame: Frame,
            op: (Int, Int) -> Int,
        ): VNumber = VNumber(
            op(
                JsSemantics.toInt32(eval(left, frame)),
                JsSemantics.toInt32(eval(right, frame)),
            ).toDouble(),
        )

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
                is VFunction, is VNativeFunction -> typeName == "Function"
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

        private fun readArray(array: VValue, index: VValue): VValue {
            return when (array) {
                is VArray -> {
                    if (index == VNamespace("Symbol.iterator")) return iteratorFactory(array)
                    val i = JsSemantics.toNumber(index)
                    if (i == floor(i) && i >= 0 && i < array.elements.size) {
                        array.elements[i.toInt()]
                    } else {
                        VUndefined
                    }
                }

                is VString -> {
                    if (index == VNamespace("Symbol.iterator")) return iteratorFactory(array)
                    val i = JsSemantics.toNumber(index)
                    if (i == floor(i) && i >= 0 && i < array.value.length) {
                        VString(array.value[i.toInt()].toString())
                    } else {
                        VUndefined
                    }
                }

                VNull, VUndefined -> throw typeError("cannot read index of ${JsSemantics.toStringJs(array)}")

                is VObject -> {
                    if (array in iterators && index == VNamespace("Symbol.iterator")) {
                        return iteratorMethod(array, "Symbol.iterator")
                    }
                    val property = if (index == VNamespace("Symbol.iterator")) {
                        "Symbol.iterator"
                    } else {
                        JsSemantics.toStringJs(index)
                    }
                    readObjectProperty(array, property)
                }

                is VMap, is VSet ->
                    if (index == VNamespace("Symbol.iterator")) iteratorFactory(array) else VUndefined

                else -> VUndefined
            }
        }

        private fun readField(instance: VValue, name: String): VValue = when (instance) {
            is VObject -> if (instance in iterators && name in setOf("next", "Symbol.iterator")) {
                iteratorMethod(instance, name)
            } else {
                readObjectProperty(instance, name)
            }
            is VArray -> when (name) {
                "length" -> VNumber(instance.elements.size.toDouble())
                "Symbol.iterator" -> iteratorFactory(instance)
                else -> VUndefined
            }

            is VString -> when (name) {
                "length" -> VNumber(instance.value.length.toDouble())
                "Symbol.iterator" -> iteratorFactory(instance)
                else -> VUndefined
            }

            is VMap -> when (name) {
                "size" -> VNumber(instance.entries.size.toDouble())
                "Symbol.iterator" -> iteratorFactory(instance)
                else -> VUndefined
            }

            is VSet -> when (name) {
                "size" -> VNumber(instance.elements.size.toDouble())
                "Symbol.iterator" -> iteratorFactory(instance)
                else -> VUndefined
            }
            is VNamespace -> {
                if (instance.name.startsWith("module:")) {
                    val modulePath = instance.name.removePrefix("module:")
                    resolver.resolveModuleExport(modulePath, name)?.let { method ->
                        methodFunctionCache.getOrPut(method) { VFunction(method) }
                    }
                        ?: if (resolver.hasDeclaredExport(modulePath, name)) {
                            throw UnsupportedFeatureSignal("module export not materialized: $modulePath#$name")
                        } else if (resolver.hasModule(modulePath) && !resolver.hasExactExportIndex(modulePath)) {
                            throw UnsupportedFeatureSignal("module export index unavailable: $modulePath")
                        } else if (resolver.hasModule(modulePath)) {
                            VUndefined
                        } else {
                            throw UnsupportedFeatureSignal("module namespace unavailable: $modulePath")
                        }
                } else {
                    Intrinsics.namespaceField(instance.name, name)
                        ?: throw UnsupportedFeatureSignal("namespace field: ${instance.name}.$name")
                }
            }

            VNull, VUndefined ->
                throw typeError("cannot read property '$name' of ${JsSemantics.toStringJs(instance)}")

            else -> VUndefined
        }

        private fun readObjectProperty(
            instance: VObject,
            name: String,
        ): VValue {
            explicitObjectProperty(instance, name)?.let { return it.value }
            instance.cls?.let { cls ->
                resolver.resolveInstanceMethod(cls, name)?.let { method ->
                    return methodFunctionCache.getOrPut(method) { VFunction(method) }
                }
            }
            return VUndefined
        }

        private fun explicitObjectProperty(instance: VObject, name: String): ObjectProperty? {
            var current: VObject? = instance
            val visited = java.util.Collections.newSetFromMap(IdentityHashMap<VObject, Boolean>())
            while (current != null && visited.add(current)) {
                if (current.fields.containsKey(name)) {
                    return ObjectProperty(current.fields.getValue(name))
                }
                current = current.prototype
            }
            return null
        }

        private fun propertyIn(key: VValue, container: VValue): Boolean {
            val property = JsSemantics.toStringJs(key)
            return when (container) {
                is VObject -> {
                    var current: VObject? = container
                    val visited = java.util.Collections.newSetFromMap(IdentityHashMap<VObject, Boolean>())
                    while (current != null && visited.add(current)) {
                        if (current.fields.containsKey(property)) return true
                        current = current.prototype
                    }
                    container.cls?.let { resolver.resolveInstanceMethod(it, property) } != null
                }

                is VArray -> {
                    property == "length" ||
                        property.toIntOrNull()?.let { it in container.elements.indices } == true
                }
                is VMap, is VSet -> property == "size"
                VNull, VUndefined, is VBool, is VNumber, is VString ->
                    throw typeError("right-hand side of 'in' is ${JsSemantics.toStringJs(container)}")

                is VFunction, is VNamespace ->
                    throw UnsupportedFeatureSignal("property membership on ${container::class.simpleName}")
            }
        }

        private fun iteratorFactory(receiver: VValue): VObject = nativeFunction(
            receiver,
            "Symbol.iterator:factory",
            NativeCallable.IteratorFactory(receiver),
        )

        private fun iteratorMethod(iterator: VObject, method: String): VObject = nativeFunction(
            iterator,
            "iterator:$method",
            NativeCallable.IteratorMethod(iterator, method),
        )

        private fun nativeFunction(
            receiver: VValue,
            key: String,
            callable: NativeCallable,
        ): VObject = nativeFunctionCache.getOrPut(receiver) { mutableMapOf() }.getOrPut(key) {
            VNativeFunction().also { nativeCallables[it] = callable }
        }

        private fun createIterator(receiver: VValue, kind: String = "default"): VObject {
            if (receiver is VObject && receiver in iterators) return receiver
            if (receiver !is VArray && receiver !is VString && receiver !is VMap && receiver !is VSet) {
                throw UnsupportedFeatureSignal(
                    "Symbol.iterator exact subset does not include ${receiver::class.simpleName}",
                )
            }
            return VObject(cls = null).also { iterators[it] = IteratorState(receiver, kind) }
        }

        private fun callIterator(iterator: VObject, method: String): VValue? {
            val state = iterators[iterator] ?: return null
            return when (method) {
                "Symbol.iterator" -> iterator
                "next" -> {
                    val (present, value) = iteratorValue(state)
                    iteratorResult(value, done = !present)
                }

                else -> null
            }
        }

        private fun iteratorValue(state: IteratorState): Pair<Boolean, VValue> {
            if (state.finished) return false to VUndefined
            val values: List<VValue> = when (val receiver = state.receiver) {
                is VArray -> receiver.elements
                is VString -> receiver.value.codePoints().toArray().map { codePoint ->
                    VString(String(Character.toChars(codePoint)))
                }

                is VMap -> when (state.kind) {
                    "keys" -> receiver.entries.keys.toList()
                    "values" -> receiver.entries.values.toList()
                    else -> receiver.entries.map { (key, value) -> VArray(mutableListOf(key, value)) }
                }

                is VSet -> when (state.kind) {
                    "entries" -> receiver.elements.map { value -> VArray(mutableListOf(value, value)) }
                    else -> receiver.elements.toList()
                }

                else -> error("validated iterator receiver changed kind")
            }
            if (state.index >= values.size) {
                state.finished = true
                return false to VUndefined
            }
            return true to values[state.index++]
        }

        private fun iteratorResult(value: VValue, done: Boolean): VObject = VObject(
            cls = null,
            fields = linkedMapOf(
                "value" to value,
                "done" to VBool(done),
            ),
        )

        private fun invokeNative(callableValue: VObject): VValue? =
            when (val callable = nativeCallables[callableValue] ?: return null) {
                is NativeCallable.IteratorFactory -> createIterator(callable.receiver)
                is NativeCallable.IteratorMethod -> callIterator(callable.iterator, callable.method)
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
                            if (i >= limits.maxArrayLength) {
                                throw BudgetExceededSignal(
                                    "array index ${JsSemantics.numberToString(i)} exceeds concrete limit " +
                                        "${limits.maxArrayLength}"
                                )
                            }
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
                            val n = checkedConcreteArrayLength(
                                JsSemantics.toNumber(value),
                                limits.maxArrayLength,
                            )
                            while (target.elements.size > n) target.elements.removeAt(target.elements.size - 1)
                            while (target.elements.size < n) target.elements.add(VUndefined)
                        }

                        VNull, VUndefined ->
                            throw typeError(
                                "cannot set property '${lhv.field.name}' of ${JsSemantics.toStringJs(target)}",
                            )

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
                is EtsInstanceCallExpr -> evalInstanceCall(expr, frame, args)
                is EtsStaticCallExpr -> evalStaticCall(expr, frame, args)
                is EtsPtrCallExpr -> evalPointerCall(expr, frame, args)

                else -> throw UnsupportedFeatureSignal("call kind: ${expr::class.simpleName}")
            }
        }

        private fun evalInstanceCall(
            expr: EtsInstanceCallExpr,
            frame: Frame,
            args: List<VValue>,
        ): VValue {
            val receiver = eval(expr.instance, frame)
            val name = expr.callee.name
            specialInstanceCall(receiver, expr.instance.name, name, args)?.let { return it }

            if (receiver == VNull || receiver == VUndefined) {
                throw typeError("cannot call '$name' of ${JsSemantics.toStringJs(receiver)}")
            }
            if (receiver is VObject) {
                explicitObjectProperty(receiver, name)?.let { property ->
                    val member = property.value
                    if (member is VFunction) {
                        return runMethod(member.method, functionThis(member, receiver), args)
                    }
                    throw typeError("property '$name' is not callable")
                }
                receiver.cls?.let { cls ->
                    resolver.resolveInstanceMethod(cls, name)?.let { callee ->
                        return runMethod(callee, receiver, args)
                    }
                }
            }
            if (name == CONSTRUCTOR_NAME) {
                constructIntrinsic(receiver, args)?.let { return it }
            }
            return Intrinsics.callInstance(receiver, name, args, ::invokeFunction)
                ?: throw UnsupportedFeatureSignal("instance method: $name on ${describeReceiver(receiver)}")
        }

        private fun specialInstanceCall(
            receiver: VValue,
            receiverName: String,
            name: String,
            args: List<VValue>,
        ): VValue? {
            if (receiver is VNamespace && receiver.name.startsWith("Object.prototype.") && name == "call") {
                val method = receiver.name.substringAfterLast('.')
                return Intrinsics.callObjectPrototype(
                    method,
                    args.getOrElse(0) { VUndefined },
                    args.drop(1),
                ) ?: throw UnsupportedFeatureSignal("Object.prototype.$method.call")
            }
            if (receiver is VFunction && (name == "call" || name == "apply")) {
                val thisArg = args.getOrElse(0) { VUndefined }
                return runMethod(
                    receiver.method,
                    functionThis(receiver, thisArg),
                    forwardedFunctionArgs(name, args),
                )
            }
            if (receiver is VObject) {
                callIterator(receiver, name)?.let { return it }
                if (receiver in iterators && name == "return") {
                    throw typeError("built-in iterator.return is not callable")
                }
            }
            if (name == "Symbol.iterator" && (receiver is VArray || receiver is VString)) {
                return createIterator(receiver)
            }
            if (receiver is VMap && name in ITERATOR_METHODS) return createIterator(receiver, name)
            if (receiver is VSet && name in ITERATOR_METHODS) {
                return createIterator(receiver, if (name == "entries") "entries" else "values")
            }
            if (receiver is VNamespace) {
                if (receiver.name.startsWith("module:")) {
                    val modulePath = receiver.name.removePrefix("module:")
                    resolver.resolveModuleExport(modulePath, name)?.let { method ->
                        return runMethod(method, VUndefined, args)
                    }
                    if (resolver.hasDeclaredExport(modulePath, name)) {
                        throw UnsupportedFeatureSignal("module export not callable: $modulePath#$name")
                    }
                    if (resolver.hasModule(modulePath) && !resolver.hasExactExportIndex(modulePath)) {
                        throw UnsupportedFeatureSignal("module export index unavailable: $modulePath")
                    }
                    if (resolver.hasModule(modulePath)) {
                        throw typeError("module export '$name' is not callable")
                    }
                    throw UnsupportedFeatureSignal("module namespace unavailable: $modulePath")
                }
                return Intrinsics.callNamespace(receiver.name, name, args)
                    ?: throw UnsupportedFeatureSignal("intrinsic: ${receiver.name}.$name")
            }
            if (receiver == VUndefined && receiverName in Intrinsics.NAMESPACES) {
                return Intrinsics.callNamespace(receiverName, name, args)
                    ?: throw UnsupportedFeatureSignal("intrinsic: $receiverName.$name")
            }
            return null
        }

        private fun forwardedFunctionArgs(name: String, args: List<VValue>): List<VValue> {
            if (name != "apply") return args.drop(1)
            return when (val list = args.getOrElse(1) { VUndefined }) {
                is VArray -> list.elements.toList()
                VNull, VUndefined -> emptyList()
                else -> throw UnsupportedFeatureSignal("Function.prototype.apply array-like argument")
            }
        }

        private fun constructIntrinsic(receiver: VValue, args: List<VValue>): VValue? {
            return when (receiver) {
                is VObject -> {
                    args.firstOrNull()?.let { receiver.fields["message"] = it }
                    receiver
                }

                is VArray -> {
                    receiver.elements.clear()
                    if (args.size == 1 && args[0] is VNumber) {
                        val size = checkedConcreteArrayLength((args[0] as VNumber).value, limits.maxArrayLength)
                        repeat(size) { receiver.elements.add(VUndefined) }
                    } else {
                        receiver.elements.addAll(args)
                    }
                    receiver
                }

                is VMap, is VSet -> {
                    when {
                        args.isEmpty() || args[0] == VUndefined || args[0] == VNull -> receiver
                        receiver is VSet && args[0] is VArray -> {
                            receiver.elements.addAll((args[0] as VArray).elements)
                            receiver
                        }

                        else -> throw UnsupportedFeatureSignal("iterable constructor argument for Map/Set")
                    }
                }

                else -> null
            }
        }

        private fun describeReceiver(receiver: VValue): String = when (receiver) {
            is VObject -> "VObject(${receiver.cls?.signature ?: "<record>"})"
            is VFunction -> "VFunction(${receiver.method.signature})"
            else -> receiver::class.simpleName ?: "unknown"
        }

        private fun evalStaticCall(
            expr: EtsStaticCallExpr,
            frame: Frame,
            args: List<VValue>,
        ): VValue {
            val className = expr.callee.enclosingClass.name
            val freeCall = className.isBlank() || className == DEFAULT_ARK_CLASS_NAME
            if (freeCall && frame.locals.containsKey(expr.callee.name)) {
                val dynamic = frame.locals.getValue(expr.callee.name)
                if (dynamic is VFunction) {
                    return runMethod(dynamic.method, functionThis(dynamic, VUndefined), args)
                }
                throw typeError("${JsSemantics.toStringJs(dynamic)} is not callable")
            }
            Intrinsics.callNamespace(className, expr.callee.name, args)?.let { return it }
            resolver.resolveStaticMethod(expr.callee)?.let { callee ->
                return runMethod(callee, VUndefined, args)
            }
            if (freeCall) {
                callConversion(expr.callee.name, args)?.let { return it }
            }
            throw UnsupportedFeatureSignal("static callee not found: ${expr.callee}")
        }

        private fun evalPointerCall(
            expr: EtsPtrCallExpr,
            frame: Frame,
            args: List<VValue>,
        ): VValue {
            val ptrValue = eval(expr.ptr, frame)
            if (ptrValue is VFunction) {
                val receiver = frame.temporaryCallReceivers[expr.ptr.name] ?: VUndefined
                return runMethod(ptrValue.method, functionThis(ptrValue, receiver), args)
            }
            if (ptrValue is VObject && nativeCallables.containsKey(ptrValue)) {
                return invokeNative(ptrValue)
                    ?: throw UnsupportedFeatureSignal("native ptr call: ${expr.callee.name}")
            }
            if (ptrValue != VUndefined) {
                val globalConversionReference = !frame.locals.containsKey(expr.ptr.name) &&
                    expr.ptr.name == expr.callee.name
                val loweredConversionArgument = expr.ptr.name != expr.callee.name
                if (globalConversionReference || loweredConversionArgument) {
                    callConversion(expr.callee.name, args)?.let { return it }
                }
                throw typeError("${JsSemantics.toStringJs(ptrValue)} is not callable")
            }
            callConversion(expr.callee.name, args)?.let { return it }
            val callee = resolver.resolveFunctionPointer(expr.callee)
                ?: throw UnsupportedFeatureSignal("ptr call: ${expr.callee.name}")
            return runMethod(callee, VUndefined, args)
        }

        private fun callConversion(name: String, args: List<VValue>): VValue? {
            if (name != "Array") return Intrinsics.callConversion(name, args)
            if (args.isEmpty()) return VArray()
            if (args.size != 1 || args[0] !is VNumber) return VArray(args.toMutableList())

            val size = checkedConcreteArrayLength(
                (args[0] as VNumber).value,
                limits.maxArrayLength,
            )
            return VArray(MutableList(size) { VUndefined })
        }

        /** Host callback for higher-order intrinsics (`arr.map(f)`, `map.forEach(f)`, ...). */
        private fun invokeFunction(fn: VFunction, args: List<VValue>): VValue =
            runMethod(fn.method, functionThis(fn, VUndefined), args)

        private fun functionThis(fn: VFunction, dynamicReceiver: VValue): VValue =
            when (fn.thisMode) {
                VFunctionThisMode.DYNAMIC -> dynamicReceiver
                VFunctionThisMode.LEXICAL, VFunctionThisMode.BOUND -> fn.thisValue
            }
    }

    private companion object {
        val ITERATOR_METHODS = setOf("Symbol.iterator", "keys", "values", "entries")
    }
}
