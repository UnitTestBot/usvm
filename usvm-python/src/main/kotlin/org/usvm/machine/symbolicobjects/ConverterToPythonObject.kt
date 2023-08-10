package org.usvm.machine.symbolicobjects

import io.ksmt.expr.KInt32NumExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.language.VirtualPythonObject
import org.usvm.language.types.*
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.emptyNamespace
import org.usvm.machine.utils.DefaultValueProvider
import org.usvm.machine.utils.PyModelHolder

class ConverterToPythonObject(
    private val ctx: UContext,
    private val typeSystem: PythonTypeSystem,
    val modelHolder: PyModelHolder
) {
    private val defaultValueProvider = DefaultValueProvider(typeSystem)
    val forcedConcreteTypes = mutableMapOf<UHeapRef, PythonType>()
    private val constructedObjects = mutableMapOf<UHeapRef, PythonObject>()
    private val virtualObjects = mutableSetOf<Pair<VirtualPythonObject, PythonObject>>()
    private var numberOfGeneratedVirtualObjects: Int = 0
    init {
        restart()
    }
    fun restart() {
        constructedObjects.clear()
        virtualObjects.clear()
        val nullRef = modelHolder.model.eval(ctx.nullRef) as UConcreteHeapRef
        val defaultObject = constructVirtualObject(InterpretedInputSymbolicPythonObject(nullRef, modelHolder))
        constructedObjects[ctx.nullRef] = defaultObject
        numberOfGeneratedVirtualObjects = 0
    }
    fun getPythonVirtualObjects(): Collection<PythonObject> = virtualObjects.map { it.second }
    fun getUSVMVirtualObjects(): Set<VirtualPythonObject> = virtualObjects.map { it.first }.toSet()
    fun numberOfVirtualObjectUsages(): Int = numberOfGeneratedVirtualObjects

    fun convert(obj: InterpretedInputSymbolicPythonObject): PythonObject {
        require(obj.modelHolder == modelHolder)
        val cached = constructedObjects[obj.address]
        if (cached != null)
            return cached
        val result = when (val type = obj.getFirstType() ?: error("Type stream for interpreted object is empty")) {
            TypeOfVirtualObject -> constructVirtualObject(obj)
            pythonInt -> convertInt(obj)
            pythonBool -> convertBool(obj)
            pythonNoneType -> ConcretePythonInterpreter.eval(emptyNamespace, "None")
            pythonList -> convertList(obj)
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

    private fun constructFromDefaultConstructor(type: ConcretePythonType): PythonObject {
        return ConcretePythonInterpreter.callStandardNew(type.asObject)
    }

    private fun constructVirtualObject(obj: InterpretedInputSymbolicPythonObject): PythonObject {
        val default = forcedConcreteTypes[obj.address]?.let { defaultValueProvider.provide(it) }
        if (default != null)
            return default

        numberOfGeneratedVirtualObjects += 1
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

    private fun convertList(obj: InterpretedInputSymbolicPythonObject): PythonObject = with(ctx) {
        val size = obj.modelHolder.model.uModel.heap.readArrayLength(obj.address, pythonList) as KInt32NumExpr
        val resultList = ConcretePythonInterpreter.makeList(emptyList())
        constructedObjects[obj.address] = resultList
        val listOfPythonObjects = List(size.value) { index ->
            val indexExpr = mkSizeExpr(index)
            val element = obj.modelHolder.model.uModel.heap.readArrayIndex(obj.address, indexExpr, pythonList, addressSort) as UConcreteHeapRef
            val elemInterpretedObject = InterpretedInputSymbolicPythonObject(element, obj.modelHolder)
            convert(elemInterpretedObject)
        }
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.addObjectToNamespace(namespace, resultList, "x")
        listOfPythonObjects.forEach {
            ConcretePythonInterpreter.addObjectToNamespace(namespace, it, "y")
            ConcretePythonInterpreter.concreteRun(namespace, "x.append(y)")
        }
        ConcretePythonInterpreter.decref(namespace)
        return resultList
    }
}