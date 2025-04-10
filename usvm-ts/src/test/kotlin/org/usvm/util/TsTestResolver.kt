@file:Suppress("CONTEXT_RECEIVERS_DEPRECATED")

package org.usvm.util

import io.ksmt.expr.KBitVec32Value
import io.ksmt.utils.asExpr
import mu.KotlinLogging
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsLiteralType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNeverType
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsPrimitiveType
import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsVoidType
import org.jacodb.ets.utils.UNKNOWN_CLASS_NAME
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.GlobalFieldValue
import org.usvm.api.TsParametersState
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.isTrue
import org.usvm.machine.TsContext
import org.usvm.machine.expr.extractBool
import org.usvm.machine.expr.extractDouble
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.memory.ULValue
import org.usvm.memory.UReadOnlyMemory
import org.usvm.mkSizeExpr
import org.usvm.model.UModelBase
import org.usvm.types.first

private val logger = KotlinLogging.logger {}

class TsTestResolver {
    fun resolve(method: EtsMethod, state: TsState): TsTest = with(state.ctx) {
        val model = state.models.first()
        val memory = state.memory

        val beforeMemoryScope = MemoryScope(model, memory, method)
        val afterMemoryScope = MemoryScope(model, memory, method)

        val returnValue = when (val res = state.methodResult) {
            is TsMethodResult.NoCall -> {
                error("No result found")
            }

            is TsMethodResult.Success -> {
                afterMemoryScope.withMode(ResolveMode.CURRENT) {
                    resolveExpr(res.value, res.value, method.returnType)
                }
            }

            is TsMethodResult.TsException -> {
                resolveException(res, afterMemoryScope)
            }
        }

        val before = beforeMemoryScope.withMode(ResolveMode.MODEL) { resolveState() }
        val after = afterMemoryScope.withMode(ResolveMode.CURRENT) { resolveState() }

        TsTest(method, before, after, returnValue, trace = emptyList())
    }

    @Suppress("unused")
    private fun resolveException(
        res: TsMethodResult.TsException,
        afterMemoryScope: MemoryScope,
    ): TsTestValue.TsException {
        // TODO support exceptions
        return TsTestValue.TsException
    }

    private class MemoryScope(
        model: UModelBase<EtsType>,
        finalStateMemory: UReadOnlyMemory<EtsType>,
        method: EtsMethod,
    ) : TsTestStateResolver(model, finalStateMemory, method) {
        context(TsContext)
        fun resolveState(): TsParametersState {
            // TODO: val properties = resolveProperties()
            //  capture Map<UHeapRef, List<String>>
            val thisInstance = resolveThisInstance()
            val parameters = resolveParameters()
            val globals = resolveGlobals()
            return TsParametersState(thisInstance, parameters, globals)
        }
    }
}

open class TsTestStateResolver(
    private val model: UModelBase<EtsType>,
    private val finalStateMemory: UReadOnlyMemory<EtsType>,
    val method: EtsMethod,
) {
    context(TsContext)
    fun resolveLValue(
        lValue: ULValue<*, *>,
        type: EtsType,
    ): TsTestValue {
        val expr = memory.read(lValue)
        val symbolicRef = if (lValue.sort == addressSort) {
            finalStateMemory.read(lValue).asExpr(addressSort)
        } else {
            null
        }
        return resolveExpr(expr, symbolicRef, type)
    }

    context(TsContext)
    fun resolveExpr(
        expr: UExpr<*>,
        symbolicRef: UExpr<*>? = null,
        type: EtsType,
    ): TsTestValue {
        if (expr.isFakeObject()) {
            return resolveFakeObject(expr, type)
        }

        val concreteRef = model.eval(expr)
        if (concreteRef is UConcreteHeapRef && concreteRef.address == 0) {
            logger.warn { "Resolved 0x0 to undefined" }
            return TsTestValue.TsUndefined
        }
        if (concreteRef == mkTsNullValue()) {
            logger.warn { "Resolved null" }
            return TsTestValue.TsNull
        }

        return when (type) {
            is EtsPrimitiveType -> {
                resolvePrimitive(expr, type)
            }

            is EtsRefType -> {
                val finalStateMemoryRef = symbolicRef?.asExpr(addressSort) ?: expr.asExpr(addressSort)
                resolveTsValue(expr.asExpr(addressSort), finalStateMemoryRef, type)
            }

            else -> {
                resolveUnknownExpr(expr, symbolicRef)
            }
        }
    }

    context(TsContext)
    fun resolveUnknownExpr(
        expr: UExpr<*>,
        finalStateMemoryRef: UExpr<*>?,
    ): TsTestValue {
        check(!expr.isFakeObject())
        return when (expr.sort) {
            fp64Sort -> {
                resolvePrimitive(expr, EtsNumberType)
            }

            boolSort -> {
                resolvePrimitive(expr, EtsBooleanType)
            }

            addressSort -> {
                resolveTsValue(
                    expr.asExpr(addressSort),
                    finalStateMemoryRef?.asExpr(addressSort),
                    EtsUnknownType
                )
            }

            else -> TODO("Unsupported sort: ${expr.sort}")
        }
    }

    context(TsContext)
    private fun resolveTsValue(
        heapRef: UHeapRef,
        finalStateMemoryRef: UHeapRef?,
        type: EtsType,
    ): TsTestValue {
        val concreteRef = model.eval(heapRef) as UConcreteHeapRef
        if (concreteRef.address == 0) {
            logger.warn { "Resolved 0x0 to undefined" }
            return TsTestValue.TsUndefined
        }
        if (concreteRef == mkTsNullValue()) {
            logger.warn { "Resolved null" }
            return TsTestValue.TsNull
        }

        return when (type) {
            // TODO add better support
            is EtsUnclearType -> {
                val classes = scene.projectAndSdkClasses.filter { it.name == type.typeName }
                if (classes.size != 1) {
                    logger.warn { "Could not uniquely resolve class: $type" }
                    return TsTestValue.TsUndefined
                }
                val cls = classes.single()
                resolveTsClass(concreteRef, finalStateMemoryRef ?: heapRef, cls.type)
            }

            is EtsClassType -> {
                resolveTsClass(concreteRef, finalStateMemoryRef ?: heapRef, type)
            }

            is EtsArrayType -> {
                resolveTsArray(concreteRef, finalStateMemoryRef ?: heapRef, type)
            }

            is EtsUnknownType -> {
                val finalType = heapRef.getTypeStream(finalStateMemory).first()
                resolveTsValue(heapRef, finalStateMemoryRef, finalType)
            }

            else -> {
                logger.info { "Unexpected type ${type::class.simpleName}: $type" }
                TsTestValue.TsUndefined
            }
        }
    }

    context(TsContext)
    private fun resolveTsArray(
        concreteRef: UConcreteHeapRef,
        heapRef: UHeapRef,
        type: EtsArrayType,
    ): TsTestValue.TsArray<*> {
        val arrayLength = mkArrayLengthLValue(heapRef, type)
        val length = model.eval(memory.read(arrayLength)) as KBitVec32Value

        val values = (0 until length.intValue).map {
            val index = mkSizeExpr(it)
            val lValue = mkArrayIndexLValue(addressSort, heapRef, index, type)
            val value = memory.read(lValue)

            if (model.eval(mkHeapRefEq(value, mkUndefinedValue())).isTrue) {
                return@map TsTestValue.TsUndefined
            }

            check(value.isFakeObject()) { "Only fake objects are allowed in arrays" }

            // TODO: element type
            resolveFakeObject(value, EtsUnknownType)
        }

        return TsTestValue.TsArray(values)
    }

    context(TsContext)
    fun resolveThisInstance(): TsTestValue {
        // TODO: resolve "static this instance"
        //       Probably we do not need this, since "static instance" should be placed onto the same
        //       register index when the method is called/analyzed.
        // if (method.isStatic || method.name == STATIC_INIT_METHOD_NAME) {
        //     val instance = state.getStaticInstance(method.enclosingClass!!)
        //     resolveExpr(instance, null, method.enclosingClass!!.type)
        // }
        val parametersCount = method.parameters.size
        val lValue = mkRegisterStackLValue(addressSort, parametersCount) // TODO check for statics
        val type = method.enclosingClass!!.type
        return resolveLValue(lValue, type)
    }

    context(TsContext)
    fun resolveParameters(): List<TsTestValue> {
        return method.parameters.mapIndexed { idx, param ->
            val ref = finalStateMemory.read(mkRegisterStackLValue(addressSort, idx))
            if (ref.isFakeObject()) {
                return@mapIndexed resolveFakeObject(ref, param.type)
            }

            val sort = typeToSort(param.type)
            val lValue = mkRegisterStackLValue(sort, idx)
            resolveLValue(lValue, param.type)
        }
    }

    context(TsContext)
    fun resolveGlobals(): Map<EtsClass, List<GlobalFieldValue>> {
        // TODO
        return emptyMap()
    }

    context(TsContext)
    private fun resolveFakeObject(
        expr: UConcreteHeapRef,
        type: EtsType? = null,
    ): TsTestValue {
        check(expr.isFakeObject())
        // Note that we need to read everything about fake objects from *final* state of memory,
        // because they are allocated objects and their details are stored in memory.
        val fakeType = expr.getFakeType(finalStateMemory)
        return when {
            model.eval(fakeType.boolTypeExpr).isTrue -> {
                val value = expr.extractBool(finalStateMemory)
                resolveExpr(model.eval(value), value, EtsBooleanType)
            }

            model.eval(fakeType.fpTypeExpr).isTrue -> {
                val value = expr.extractFp(finalStateMemory)
                resolveExpr(model.eval(value), value, EtsNumberType)
            }

            model.eval(fakeType.refTypeExpr).isTrue -> {
                val value = expr.extractRef(finalStateMemory)
                val finalType = type?.takeIf { it != EtsUnknownType && it != EtsAnyType }
                    ?: scene.projectClasses.first().type
                resolveExpr(model.eval(value), value, finalType)
            }

            else -> error("Unsupported")
        }
    }

    context(TsContext)
    private fun resolvePrimitive(
        expr: UExpr<*>,
        type: EtsPrimitiveType,
    ): TsTestValue {
        if (expr.isFakeObject()) {
            return when (type) {
                EtsBooleanType -> {
                    val value = expr.extractBool(finalStateMemory)
                    TsTestValue.TsBoolean(model.eval(value).extractBool())
                }

                EtsNumberType -> {
                    val value = expr.extractFp(finalStateMemory)
                    TsTestValue.TsNumber.TsDouble(model.eval(value).extractDouble())
                }

                EtsStringType -> {
                    // TODO: support string sort
                    val value = expr.extractFp(finalStateMemory)
                    TsTestValue.TsNumber.TsDouble(model.eval(value).extractDouble())
                }

                else -> error("Unsupported type: $type")
            }
        }

        return when (type) {
            EtsBooleanType -> {
                TsTestValue.TsBoolean(model.eval(expr).extractBool())
            }

            EtsNumberType -> {
                TsTestValue.TsNumber.TsDouble(model.eval(expr).extractDouble())
            }

            EtsStringType -> {
                TsTestValue.TsNumber.TsDouble(model.eval(expr).extractDouble())
            }

            EtsUndefinedType -> {
                TsTestValue.TsUndefined
            }

            is EtsLiteralType -> TODO()
            EtsNullType -> TODO()
            EtsNeverType -> TODO()
            EtsVoidType -> TODO()

            else -> error("Unexpected type: $type")
        }
    }

    context(TsContext)
    private fun resolveClass(
        classType: EtsClassType,
    ): EtsClass {
        // Special case for Object:
        if (classType.signature.name == "Object") {
            return createObjectClass()
        }

        // Perfect signature:
        if (classType.signature.name != UNKNOWN_CLASS_NAME) {
            val classes = scene.projectAndSdkClasses.filter { it.signature == classType.signature }
            if (classes.size == 1) {
                return classes.single()
            }
        }

        // Sad signature:
        val classes = scene.projectAndSdkClasses.filter { it.signature.name == classType.signature.name }
        if (classes.size == 1) {
            return classes.single()
        }

        error("Could not resolve class: ${classType.signature}")
    }

    context(TsContext)
    private fun resolveTsClass(
        concreteRef: UConcreteHeapRef,
        heapRef: UHeapRef,
        classType: EtsClassType,
    ): TsTestValue.TsClass {
        val clazz = resolveClass(classType)
        val properties = clazz.fields
            .filterNot { field ->
                field.modifiers.isStatic
            }
            .associate { field ->
                val sort = typeToSort(field.type)
                if (sort == unresolvedSort) {
                    val lValue = mkFieldLValue(addressSort, heapRef, field.signature)
                    val fieldExpr = memory.read(lValue)
                    val obj = if (fieldExpr.isFakeObject()) {
                        resolveExpr(fieldExpr, fieldExpr, field.type)
                    } else {
                        TsTestValue.TsUndefined
                        // TODO: maybe resolve non-fake 'fieldExpr' here?
                    }
                    field.name to obj
                } else {
                    val ref = finalStateMemory.read(mkFieldLValue(addressSort, heapRef, field))
                    if (ref.isFakeObject()) {
                        return@associate field.name to resolveFakeObject(ref, field.type)
                    }

                    val lValue = mkFieldLValue(sort, concreteRef, field)
                    val fieldExpr = memory.read(lValue)
                    val resolved = resolveExpr(fieldExpr, fieldExpr, field.type)
                    field.name to resolved
                }
            }
        return TsTestValue.TsClass(clazz.name, properties)
    }

    internal var resolveMode: ResolveMode = ResolveMode.ERROR

    val memory: UReadOnlyMemory<EtsType>
        get() = when (resolveMode) {
            ResolveMode.MODEL -> model
            ResolveMode.CURRENT -> finalStateMemory
            ResolveMode.ERROR -> error("Illegal operation for a model")
        }
}

enum class ResolveMode {
    MODEL, CURRENT, ERROR
}

fun <S : TsTestStateResolver, R> S.withMode(resolveMode: ResolveMode, body: S.() -> R): R {
    val prevValue = this.resolveMode
    try {
        this.resolveMode = resolveMode
        return body()
    } finally {
        this.resolveMode = prevValue
    }
}
