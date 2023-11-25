package org.usvm.machine.symbolicobjects

import io.ksmt.expr.KInt32NumExpr
import org.usvm.UConcreteHeapRef
import org.usvm.api.readArrayIndex
import org.usvm.api.typeStreamOf
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isStaticHeapRef
import org.usvm.language.PythonCallable
import org.usvm.language.types.*
import org.usvm.machine.UPythonContext
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.utils.MAX_INPUT_ARRAY_LENGTH
import org.usvm.machine.utils.PyModelHolder
import org.usvm.machine.utils.getMembersFromType
import org.usvm.memory.UMemory
import org.usvm.mkSizeExpr
import org.usvm.types.first

class PreConversionVisitor(
    val ctx: UPythonContext,
    private val memory: UMemory<PythonType, PythonCallable>,
    private val typeSystem: PythonTypeSystem,
    private val modelHolder: PyModelHolder,
    private val preallocatedObjects: PreallocatedObjects
) {
    private val visited = mutableSetOf<InterpretedSymbolicPythonObject>()

    fun visit(obj: InterpretedSymbolicPythonObject, symbol: UninterpretedSymbolicPythonObject, concolicRunContext: ConcolicRunContext) {
        if (obj in visited)
            return
        visited.add(obj)
        when (val type = obj.getFirstType() ?: error("Type stream for interpreted object is empty")) {
            MockType -> Unit
            typeSystem.pythonInt -> Unit
            typeSystem.pythonBool -> Unit
            typeSystem.pythonNoneType -> Unit
            typeSystem.pythonList -> visitArray(obj, symbol, concolicRunContext)
            typeSystem.pythonTuple -> visitArray(obj, symbol, concolicRunContext)
            typeSystem.pythonStr -> Unit
            typeSystem.pythonSlice -> Unit
            typeSystem.pythonFloat -> Unit
            typeSystem.pythonDict -> visitDict(obj, symbol, concolicRunContext)
            else -> {
                if ((type as? ConcretePythonType)?.let { ConcretePythonInterpreter.typeHasStandardNew(it.asObject) } == true)
                    visitCompositeObject(obj, symbol, concolicRunContext, type)
            }
        }
    }

    private fun visitArray(obj: InterpretedSymbolicPythonObject, symbol: UninterpretedSymbolicPythonObject, concolicRunContext: ConcolicRunContext) {
        require(obj is InterpretedInputSymbolicPythonObject)
        val size = obj.readArrayLength(ctx) as? KInt32NumExpr ?: throw LengthOverflowException
        if (size.value > MAX_INPUT_ARRAY_LENGTH)
            throw LengthOverflowException
        List(size.value) { index ->
            val indexExpr = ctx.mkSizeExpr(index)
            val element = obj.modelHolder.model.uModel.readArrayIndex(
                obj.address,
                indexExpr,
                ArrayType,
                ctx.addressSort
            ) as UConcreteHeapRef
            val elemInterpretedObject =
                if (isStaticHeapRef(element)) {
                    val type = memory.typeStreamOf(element).first()
                    require(type is ConcretePythonType)
                    InterpretedAllocatedOrStaticSymbolicPythonObject(element, type, typeSystem)
                } else {
                    InterpretedInputSymbolicPythonObject(element, obj.modelHolder, typeSystem)
                }
            visit(elemInterpretedObject, symbol.readArrayElement(concolicRunContext, ctx.mkIntNum(index)), concolicRunContext)
        }
    }

    private fun visitDict(obj: InterpretedSymbolicPythonObject, symbol: UninterpretedSymbolicPythonObject, concolicRunContext: ConcolicRunContext) {
        require(obj is InterpretedInputSymbolicPythonObject)
        val model = modelHolder.model.uModel
        model.possibleRefKeys.forEach {
            val key = if (isStaticHeapRef(it)) {
                val type = memory.typeStreamOf(it).first()
                require(type is ConcretePythonType)
                InterpretedAllocatedOrStaticSymbolicPythonObject(it, type, typeSystem)
            } else {
                InterpretedInputSymbolicPythonObject(it, modelHolder, typeSystem)
            }
            if (obj.dictContainsRef(key)) {
                val uninterpretedKey = UninterpretedSymbolicPythonObject(it, typeSystem)
                visit(key, uninterpretedKey, concolicRunContext)
                val value = obj.readDictRefElement(ctx, key, memory)
                visit(value, symbol.readDictRefElement(concolicRunContext, uninterpretedKey), concolicRunContext)
            }
        }
        model.possibleIntKeys.forEach {
            if (obj.dictContainsInt(ctx, it)) {
                val value = obj.readDictIntElement(ctx, it, memory)
                visit(value, symbol.readDictIntElement(concolicRunContext, it), concolicRunContext)
            }
        }
    }

    private fun visitCompositeObject(
        obj: InterpretedSymbolicPythonObject,
        symbol: UninterpretedSymbolicPythonObject,
        concolicRunContext: ConcolicRunContext,
        type: ConcretePythonType
    ) {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "Instance of type with default constructor cannot be static"
        }
        val members = getMembersFromType(type, typeSystem)
        members.forEach {
            if (it.contains('\''))
                return@forEach
            val ref = ConcretePythonInterpreter.eval(ConcretePythonInterpreter.emptyNamespace, "'$it'")
            if (preallocatedObjects.refOfString(it) == null) {
                val symbolStr = preallocatedObjects.allocateStr(concolicRunContext, it, ref)
                modelHolder.model.uModel.preallocatedObjects.inheritStrAllocation(it, ref, symbolStr)
            }
        }
        preallocatedObjects.listAllocatedStrs().forEach { strSymbol ->
            val nameAddress = modelHolder.model.eval(strSymbol.address)
            require(isStaticHeapRef(nameAddress)) { "Symbolic string object must be static" }
            val nameSymbol = InterpretedAllocatedOrStaticSymbolicPythonObject(nameAddress, typeSystem.pythonStr, typeSystem)
            if (obj.containsField(nameSymbol)) {
                val str = preallocatedObjects.concreteString(strSymbol)!!
                if (ConcretePythonInterpreter.typeLookup(type.asObject, str) == null) {
                    val interpretedSymbolicValue = obj.getFieldValue(ctx, nameSymbol, memory)
                    val uninterpreted = symbol.getFieldValue(concolicRunContext, strSymbol)
                    concolicRunContext.curState!!.pathConstraints.pythonSoftConstraints =
                        concolicRunContext.curState!!.pathConstraints.pythonSoftConstraints.add(ctx.mkHeapRefEq(ctx.nullRef, uninterpreted.address))
                    visit(interpretedSymbolicValue, uninterpreted, concolicRunContext)
                }
            }
        }
    }
}