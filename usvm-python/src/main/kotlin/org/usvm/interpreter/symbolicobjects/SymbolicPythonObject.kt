package org.usvm.interpreter.symbolicobjects

import io.ksmt.sort.KBoolSort
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.interpreter.PyModel
import org.usvm.language.*
import org.usvm.memory.UMemoryBase
import org.usvm.memory.UReadOnlySymbolicHeap

sealed class SymbolicPythonObject(
    open val address: UHeapRef,
    open val heap: UReadOnlySymbolicHeap<PropertyOfPythonObject, out PythonType>,
    private val ctx: UContext
) {
    override fun equals(other: Any?): Boolean {
        if (other !is SymbolicPythonObject)
            return false
        return address == other.address && heap == other.heap
    }

    override fun hashCode(): Int {
        return (address to heap).hashCode()
    }

    val concreteType: ConcretePythonType?
        get() = castedTo

    protected var castedTo: ConcretePythonType? = null

    fun getIntContent(): UExpr<KIntSort> {
        require(castedTo == pythonInt)
        @Suppress("unchecked_cast")
        return heap.readField(address, IntContent, ctx.intSort) as UExpr<KIntSort>
    }

    fun getBoolContent(): UExpr<KBoolSort> {
        require(castedTo == pythonBool)
        @Suppress("unchecked_cast")
        return heap.readField(address, BoolContent, ctx.boolSort) as UExpr<KBoolSort>
    }
}

class UninterpretedSymbolicPythonObject(
    override val address: UHeapRef,
    private val memory: UMemoryBase<PropertyOfPythonObject, PythonType, PythonCallable>,
    ctx: UContext,
): SymbolicPythonObject(address, memory.heap, ctx) {
    fun castToConcreteType(type: ConcretePythonType) {
        if (castedTo != null) {
            require(castedTo == type)
            return
        }
        castedTo = type
        memory.types.cast(address, type)
    }

    fun <SORT: USort> setContent(expr: UExpr<SORT>, concretePythonType: ConcretePythonType) {
        castToConcreteType(concretePythonType)
        val field =
            when (concretePythonType) {
                pythonInt -> IntContent
                pythonBool -> BoolContent
                else -> TODO()
            }
        val lvalue = UFieldLValue(expr.sort, address, field)
        memory.write(lvalue, expr)
    }
}

class InterpretedSymbolicPythonObject(
    override val address: UConcreteHeapRef,
    private val model: PyModel,
    ctx: UContext,
): SymbolicPythonObject(address, model.uModel.heap, ctx) {
    init {
        castedTo = model.uModel.types.typeOf(address.address) as ConcretePythonType
    }
}

fun interpretSymbolicPythonObject(
    obj: UninterpretedSymbolicPythonObject,
    model: PyModel,
    ctx: UContext
): InterpretedSymbolicPythonObject =
    InterpretedSymbolicPythonObject(model.eval(obj.address) as UConcreteHeapRef, model, ctx)