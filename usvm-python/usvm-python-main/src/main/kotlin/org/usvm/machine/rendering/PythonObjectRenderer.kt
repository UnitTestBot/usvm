package org.usvm.machine.rendering

import org.usvm.language.VirtualPythonObject
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter.emptyNamespace
import org.usvm.machine.interpreters.concrete.PythonObject
import org.usvm.python.model.*

class PythonObjectRenderer(private val useNoneInsteadOfMock: Boolean = false) {
    private val converted = mutableMapOf<PyObjectModel, PythonObject>()
    fun convert(model: PyObjectModel): PythonObject {
        if (model in converted)
            return converted[model]!!
        val result = when (model) {
            is PyPrimitive -> convertPrimitive(model)
            is PyIdentifier -> convertIdentifier(model)
            is PyCompositeObject -> convertCompositeObject(model)
            is PyTupleObject -> convertTuple(model)
            is PyMockObject -> convertMock(model)
        }
        converted[model] = result
        return result
    }

    fun getPythonVirtualObjects(): List<PythonObject> =
        virtualObjects.map { it.second }

    fun getUSVMVirtualObjects(): List<VirtualPythonObject> =
        virtualObjects.map { it.first }

    private fun convertPrimitive(model: PyPrimitive): PythonObject {
        return ConcretePythonInterpreter.eval(emptyNamespace, model.repr)
    }

    private fun convertIdentifier(model: PyIdentifier): PythonObject {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.concreteRun(namespace, "import ${model.module}")
        val result = ConcretePythonInterpreter.eval(namespace, "${model.module}.${model.name}")
        ConcretePythonInterpreter.decref(namespace)
        return result
    }

    private fun convertCompositeObject(model: PyCompositeObject): PythonObject {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        val constructorRef = convert(model.constructor)
        val argsRef = model.constructorArgs.map { convert(it) }
        ConcretePythonInterpreter.addObjectToNamespace(namespace, constructorRef, "constructor")
        argsRef.forEachIndexed { index, ref ->
            ConcretePythonInterpreter.addObjectToNamespace(namespace, ref, "arg_$index")
        }
        val argsRepr = List(argsRef.size) { "arg_$it" }.joinToString(separator = ", ")
        val repr = "constructor($argsRepr)"
        val result = ConcretePythonInterpreter.eval(namespace, repr)
        converted[model] = result
        ConcretePythonInterpreter.addObjectToNamespace(namespace, result, "result")
        if (model.listItems != null) {
            model.listItems!!.forEach {
                val elemRef = convert(it)
                ConcretePythonInterpreter.addObjectToNamespace(namespace, elemRef, "elem")
                ConcretePythonInterpreter.concreteRun(namespace, "result.append(elem)")
            }
        }
        if (model.dictItems != null) {
            model.dictItems!!.forEach { (key, elem) ->
                val keyRef = convert(key)
                val elemRef = convert(elem)
                ConcretePythonInterpreter.addObjectToNamespace(namespace, keyRef, "key")
                ConcretePythonInterpreter.addObjectToNamespace(namespace, elemRef, "elem")
                ConcretePythonInterpreter.concreteRun(namespace, "result[key] = elem")
            }
        }
        if (model.fieldDict != null) {
            model.fieldDict!!.forEach { (name, value) ->
                val valueRef = convert(value)
                ConcretePythonInterpreter.addObjectToNamespace(namespace, valueRef, "value")
                ConcretePythonInterpreter.concreteRun(namespace, "result.$name = value")
            }
        }
        ConcretePythonInterpreter.decref(namespace)
        return result
    }

    private fun convertTuple(model: PyTupleObject): PythonObject {
        val size = model.items.size
        val result = ConcretePythonInterpreter.allocateTuple(size)
        converted[model] = result
        model.items.forEachIndexed { index, item ->
            val itemRef = convert(item)
            ConcretePythonInterpreter.setTupleElement(result, index, itemRef)
        }
        return result
    }

    private val virtualObjects = mutableSetOf<Pair<VirtualPythonObject, PythonObject>>()
    private fun convertMock(model: PyMockObject): PythonObject {
        if (useNoneInsteadOfMock)
            return ConcretePythonInterpreter.eval(emptyNamespace, "None")
        val virtual = VirtualPythonObject(model.id)
        val result = ConcretePythonInterpreter.allocateVirtualObject(virtual)
        virtualObjects.add(virtual to result)
        return result
    }
}
