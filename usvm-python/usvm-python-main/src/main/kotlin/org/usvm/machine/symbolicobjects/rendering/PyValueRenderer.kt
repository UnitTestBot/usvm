package org.usvm.machine.symbolicobjects.rendering

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter.emptyNamespace
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.interpreters.concrete.utils.VirtualPythonObject
import org.usvm.python.model.PyCompositeObject
import org.usvm.python.model.PyIdentifier
import org.usvm.python.model.PyMockObject
import org.usvm.python.model.PyPrimitive
import org.usvm.python.model.PyTupleObject
import org.usvm.python.model.PyValue

class PyValueRenderer(private val useNoneInsteadOfMock: Boolean = false) {
    private val converted = mutableMapOf<PyValue, PyObject>()
    fun convert(model: PyValue): PyObject {
        val cached = converted[model]
        if (cached != null) {
            return cached
        }
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

    fun getPythonVirtualObjects(): List<PyObject> =
        virtualObjects.map { it.second }

    fun getUSVMVirtualObjects(): List<VirtualPythonObject> =
        virtualObjects.map { it.first }

    private fun convertPrimitive(model: PyPrimitive): PyObject {
        return ConcretePythonInterpreter.eval(emptyNamespace, model.repr)
    }

    private fun convertIdentifier(model: PyIdentifier): PyObject {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.concreteRun(namespace, "import ${model.module}")
        val result = ConcretePythonInterpreter.eval(namespace, "${model.module}.${model.name}")
        ConcretePythonInterpreter.decref(namespace)
        return result
    }

    private fun convertCompositeObject(model: PyCompositeObject): PyObject {
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
        model.listItems?.forEach {
            val elemRef = convert(it)
            ConcretePythonInterpreter.addObjectToNamespace(namespace, elemRef, "elem")
            ConcretePythonInterpreter.concreteRun(namespace, "result.append(elem)")
        }
        model.dictItems?.forEach { (key, elem) ->
            val keyRef = convert(key)
            val elemRef = convert(elem)
            ConcretePythonInterpreter.addObjectToNamespace(namespace, keyRef, "key")
            ConcretePythonInterpreter.addObjectToNamespace(namespace, elemRef, "elem")
            ConcretePythonInterpreter.concreteRun(namespace, "result[key] = elem")
        }
        model.fieldDict?.forEach { (name, value) ->
            val valueRef = convert(value)
            ConcretePythonInterpreter.addObjectToNamespace(namespace, valueRef, "value")
            ConcretePythonInterpreter.concreteRun(namespace, "result.$name = value")
        }
        ConcretePythonInterpreter.decref(namespace)
        return result
    }

    private fun convertTuple(model: PyTupleObject): PyObject {
        val size = model.items.size
        val result = ConcretePythonInterpreter.allocateTuple(size)
        converted[model] = result
        model.items.forEachIndexed { index, item ->
            val itemRef = convert(item)
            ConcretePythonInterpreter.setTupleElement(result, index, itemRef)
        }
        return result
    }

    private val virtualObjects = mutableSetOf<Pair<VirtualPythonObject, PyObject>>()
    private fun convertMock(model: PyMockObject): PyObject {
        if (useNoneInsteadOfMock) {
            return ConcretePythonInterpreter.eval(emptyNamespace, "None")
        }
        val virtual = VirtualPythonObject(model.id)
        val result = ConcretePythonInterpreter.allocateVirtualObject(virtual)
        virtualObjects.add(virtual to result)
        return result
    }
}
