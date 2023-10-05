package org.usvm.machine.symbolicobjects

import io.ksmt.expr.KInt32NumExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.language.VirtualPythonObject
import org.usvm.language.types.*
import org.usvm.machine.UPythonContext
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.ConcretePythonInterpreter.emptyNamespace
import org.usvm.machine.utils.DefaultValueProvider
import org.usvm.machine.utils.PyModelHolder
import org.usvm.mkSizeExpr

class ConverterToPythonObject(
    private val ctx: UPythonContext,
    private val typeSystem: PythonTypeSystem,
    val modelHolder: PyModelHolder
) {
    private val defaultValueProvider = DefaultValueProvider(typeSystem)
    val forcedConcreteTypes = mutableMapOf<UHeapRef, PythonType>()
    private val constructedObjects = mutableMapOf<UHeapRef, PythonObject>()
    private val virtualObjects = mutableSetOf<Pair<VirtualPythonObject, PythonObject>>()
    private var numberOfUsagesOfVirtualObjects: Int = 0
    private var strNumber = 0
    init {
        restart()
    }
    fun restart() {
        // TODO: decRefs()
        constructedObjects.clear()
        virtualObjects.clear()
        val nullRef = modelHolder.model.eval(ctx.nullRef) as UConcreteHeapRef
        val defaultObject = constructVirtualObject(InterpretedInputSymbolicPythonObject(nullRef, modelHolder, typeSystem))
        constructedObjects[ctx.nullRef] = defaultObject
        numberOfUsagesOfVirtualObjects = 0
        strNumber = 0
    }
    fun getPythonVirtualObjects(): Collection<PythonObject> = virtualObjects.map { it.second }
    fun getUSVMVirtualObjects(): Set<VirtualPythonObject> = virtualObjects.map { it.first }.toSet()
    fun numberOfVirtualObjectUsages(): Int = numberOfUsagesOfVirtualObjects

    fun convert(obj: InterpretedInputSymbolicPythonObject): PythonObject {
        require(obj.modelHolder == modelHolder)
        val cached = constructedObjects[obj.address]
        if (cached != null)
            return cached
        val result = when (val type = obj.getFirstType() ?: error("Type stream for interpreted object is empty")) {
            MockType -> constructVirtualObject(obj)
            typeSystem.pythonInt -> convertInt(obj)
            typeSystem.pythonBool -> convertBool(obj)
            typeSystem.pythonNoneType -> ConcretePythonInterpreter.eval(emptyNamespace, "None")
            typeSystem.pythonList -> convertList(obj)
            typeSystem.pythonTuple -> convertTuple(obj)
            typeSystem.pythonStr -> convertString()
            typeSystem.pythonSlice -> convertSlice(obj)
            typeSystem.pythonFloat -> convertFloat(obj)
            else -> {
                if ((type as? ConcretePythonType)?.let { ConcretePythonInterpreter.typeHasStandardNew(it.asObject) } == true)
                    constructFromDefaultConstructor(type)
                else
                    error("Could not construct instance of type $type")
            }
        }
        constructedObjects[obj.address] = result
        return result
    }

    private fun decRefs() {
        constructedObjects.values.forEach {
            ConcretePythonInterpreter.decref(it)
        }
    }

    private fun convertFloat(obj: InterpretedInputSymbolicPythonObject): PythonObject {
        val cmd = when (val floatValue = obj.getFloatContent(ctx)) {
            is FloatNan -> "float('nan')"
            is FloatPlusInfinity -> "float('inf')"
            is FloatMinusInfinity -> "float('-inf')"
            is FloatNormalValue -> floatValue.value.toString()
        }
        return ConcretePythonInterpreter.eval(emptyNamespace, cmd)
    }

    private fun convertString(): PythonObject {
        return ConcretePythonInterpreter.eval(emptyNamespace, "'${strNumber++}'")
    }

    private fun constructArrayContents(
        obj: InterpretedInputSymbolicPythonObject,
    ): List<PythonObject> {
        val size = obj.modelHolder.model.uModel.readArrayLength(obj.address, ArrayType, ctx.intSort) as KInt32NumExpr
        return List(size.value) { index ->
            val indexExpr = ctx.mkSizeExpr(index)
            val element = obj.modelHolder.model.uModel.readArrayIndex(
                obj.address,
                indexExpr,
                ArrayType,
                ctx.addressSort
            ) as UConcreteHeapRef
            if (element.address == 0 && forcedConcreteTypes[element] == null)
                numberOfUsagesOfVirtualObjects += 1
            val elemInterpretedObject = InterpretedInputSymbolicPythonObject(element, obj.modelHolder, typeSystem)
            convert(elemInterpretedObject)
        }
    }

    private fun constructFromDefaultConstructor(type: ConcretePythonType): PythonObject {
        require(type.owner == typeSystem)
        return ConcretePythonInterpreter.callStandardNew(type.asObject)
    }

    private fun constructVirtualObject(obj: InterpretedInputSymbolicPythonObject): PythonObject {
        val default = forcedConcreteTypes[obj.address]?.let { defaultValueProvider.provide(it) }
        if (default != null)
            return default

        numberOfUsagesOfVirtualObjects += 1
        val virtual = VirtualPythonObject(obj)
        val result = ConcretePythonInterpreter.allocateVirtualObject(virtual)
        virtualObjects.add(virtual to result)
        return result
    }

    private fun convertInt(obj: InterpretedInputSymbolicPythonObject): PythonObject =
        ConcretePythonInterpreter.eval(emptyNamespace, obj.getIntContent(ctx).toString())

    private fun convertBool(obj: InterpretedInputSymbolicPythonObject): PythonObject =
        when (obj.getBoolContent(ctx)) {
            ctx.trueExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "True")
            ctx.falseExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "False")
            else -> error("Not reachable")
        }

    private fun convertList(obj: InterpretedInputSymbolicPythonObject): PythonObject {
        val resultList = ConcretePythonInterpreter.makeList(emptyList())
        constructedObjects[obj.address] = resultList
        val listOfPythonObjects = constructArrayContents(obj)
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.addObjectToNamespace(namespace, resultList, "x")
        listOfPythonObjects.forEach {
            ConcretePythonInterpreter.addObjectToNamespace(namespace, it, "y")
            ConcretePythonInterpreter.concreteRun(namespace, "x.append(y)")
        }
        ConcretePythonInterpreter.decref(namespace)
        return resultList
    }

    private fun convertTuple(obj: InterpretedInputSymbolicPythonObject): PythonObject {
        val size = obj.modelHolder.model.uModel.readArrayLength(obj.address, ArrayType, ctx.intSort) as KInt32NumExpr
        val resultTuple = ConcretePythonInterpreter.allocateTuple(size.value)
        constructedObjects[obj.address] = resultTuple
        val listOfPythonObjects = constructArrayContents(obj)
        listOfPythonObjects.forEachIndexed { index, pythonObject ->
            ConcretePythonInterpreter.setTupleElement(resultTuple, index, pythonObject)
        }
        return resultTuple
    }

    private fun convertSlice(obj: InterpretedInputSymbolicPythonObject): PythonObject {
        val (start, stop, step) = obj.getSliceContent(ctx, typeSystem)
        val startStr = start?.toString() ?: "None"
        val stopStr = stop?.toString() ?: "None"
        val stepStr = step?.toString() ?: "None"
        return ConcretePythonInterpreter.eval(emptyNamespace, "slice($startStr, $stopStr, $stepStr)")
    }
}