package org.usvm.machine.symbolicobjects.rendering

import io.ksmt.expr.KInt32NumExpr
import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KIntSort
import org.usvm.UConcreteHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.isStaticHeapRef
import org.usvm.machine.PyState
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.symbolicobjects.InterpretedAllocatedOrStaticSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.memory.FloatMinusInfinity
import org.usvm.machine.symbolicobjects.memory.FloatNan
import org.usvm.machine.symbolicobjects.memory.FloatNormalValue
import org.usvm.machine.symbolicobjects.memory.FloatPlusInfinity
import org.usvm.machine.symbolicobjects.memory.containsField
import org.usvm.machine.symbolicobjects.memory.dictContainsInt
import org.usvm.machine.symbolicobjects.memory.dictContainsRef
import org.usvm.machine.symbolicobjects.memory.dictIsEmpty
import org.usvm.machine.symbolicobjects.memory.getBoolContent
import org.usvm.machine.symbolicobjects.memory.getFieldValue
import org.usvm.machine.symbolicobjects.memory.getFloatContent
import org.usvm.machine.symbolicobjects.memory.getIntContent
import org.usvm.machine.symbolicobjects.memory.getSliceContent
import org.usvm.machine.symbolicobjects.memory.readArrayElement
import org.usvm.machine.symbolicobjects.memory.readArrayLength
import org.usvm.machine.symbolicobjects.memory.readDictIntElement
import org.usvm.machine.symbolicobjects.memory.readDictRefElement
import org.usvm.machine.symbolicobjects.memory.setContainsInt
import org.usvm.machine.symbolicobjects.memory.setContainsRef
import org.usvm.machine.symbolicobjects.memory.setIsEmpty
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.MockType
import org.usvm.machine.utils.MAX_INPUT_ARRAY_LENGTH
import org.usvm.mkSizeExpr
import org.usvm.python.model.PyCompositeObject
import org.usvm.python.model.PyIdentifier
import org.usvm.python.model.PyMockObject
import org.usvm.python.model.PyPrimitive
import org.usvm.python.model.PyTupleObject
import org.usvm.python.model.PyValue
import org.usvm.types.first

class PyValueBuilder(
    var state: PyState,
    private val modelHolder: PyModelHolder,
) {
    init {
        require(state.pyModel == modelHolder.model)
    }

    private val converted = mutableMapOf<UConcreteHeapRef, PyValue>()
    fun convert(obj: InterpretedSymbolicPythonObject): PyValue {
        if (obj is InterpretedInputSymbolicPythonObject) {
            require(obj.modelHolder.model == state.pyModel) {
                "Models in PyState and in InterpretedSymbolicPythonObject must be the same"
            }
        }
        require(!isAllocatedConcreteHeapRef(obj.address)) {
            "Cannot convert allocated objects"
        }
        val cached = converted[obj.address]
        if (cached != null) {
            return cached
        }
        val typeSystem = state.typeSystem
        val type = obj.getFirstType() ?: error("Type stream for interpreted object is empty")
        val result: PyValue = when (type) {
            MockType -> {
                convertMockType(obj)
            }
            typeSystem.pythonInt -> {
                convertInt(obj)
            }
            typeSystem.pythonBool -> {
                convertBool(obj)
            }
            typeSystem.pythonNoneType -> {
                convertNone()
            }
            typeSystem.pythonSlice -> {
                convertSlice(obj)
            }
            typeSystem.pythonFloat -> {
                convertFloat(obj)
            }
            typeSystem.pythonStr -> {
                convertString(obj)
            }
            typeSystem.pythonList -> {
                convertList(obj)
            }
            typeSystem.pythonTuple -> {
                convertTuple(obj)
            }
            typeSystem.pythonDict -> {
                convertDict(obj)
            }
            typeSystem.pythonSet -> {
                convertSet(obj)
            }
            else -> {
                if ((type as? ConcretePythonType)?.let {
                        ConcretePythonInterpreter.typeHasStandardNew(it.asObject)
                    } == true
                ) {
                    convertFromDefaultConstructor(obj, type)
                } else {
                    error("Could not construct instance of type $type")
                }
            }
        }
        converted[obj.address] = result
        return result
    }

    private val defaultValueProvider = DefaultPyValueProvider(state.typeSystem)
    private fun convertMockType(obj: InterpretedSymbolicPythonObject): PyValue {
        val default = modelHolder.model.forcedConcreteTypes[obj.address]?.let {
            defaultValueProvider.provide(it)
        }
        if (default != null) {
            return default
        }
        return PyMockObject(obj.address.address)
    }

    private fun convertInt(obj: InterpretedSymbolicPythonObject): PyValue =
        PyPrimitive(obj.getIntContent(state.ctx, state.memory).toString())

    private fun convertBool(obj: InterpretedSymbolicPythonObject): PyValue {
        val repr = when (obj.getBoolContent(state.ctx, state.memory)) {
            state.ctx.trueExpr -> "True"
            state.ctx.falseExpr -> "False"
            else -> error("Not reachable")
        }
        return PyPrimitive(repr)
    }

    private fun convertNone(): PyValue =
        PyPrimitive("None")

    private fun convertSlice(obj: InterpretedSymbolicPythonObject): PyValue {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "Slice cannot be static"
        }
        val (start, stop, step) = obj.getSliceContent(state.ctx, state.typeSystem)
        val startStr = start?.toString() ?: "None"
        val stopStr = stop?.toString() ?: "None"
        val stepStr = step?.toString() ?: "None"
        return PyCompositeObject(
            state.typeSystem.pythonSlice.id,
            listOf(PyPrimitive(startStr), PyPrimitive(stopStr), PyPrimitive(stepStr))
        )
    }

    private fun convertFloat(obj: InterpretedSymbolicPythonObject): PyValue {
        val repr = when (val floatValue = obj.getFloatContent(state.ctx, state.memory)) {
            is FloatNan -> "float('nan')"
            is FloatPlusInfinity -> "float('inf')"
            is FloatMinusInfinity -> "float('-inf')"
            is FloatNormalValue -> floatValue.value.toString()
        }
        return PyPrimitive(repr)
    }

    private var strNumber = 0
    private fun convertString(obj: InterpretedSymbolicPythonObject): PyValue {
        if (isStaticHeapRef(obj.address)) {
            val uninterpreted = UninterpretedSymbolicPythonObject(obj.address, state.typeSystem)
            val str = state.preAllocatedObjects.concreteString(uninterpreted)
            val ref = str?.let { state.preAllocatedObjects.refOfString(str) }
            if (ref != null) {
                val repr = ConcretePythonInterpreter.getPythonObjectRepr(ref)
                return PyPrimitive(repr)
            }
        }
        return PyPrimitive("'${strNumber++}'")
    }

    private fun convertList(obj: InterpretedSymbolicPythonObject): PyValue {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "List object cannot be static"
        }
        val result = PyCompositeObject(PyIdentifier("builtins", "list"), emptyList())
        converted[obj.address] = result
        result.listItems = constructArrayContents(obj)
        return result
    }

    private fun convertTuple(obj: InterpretedSymbolicPythonObject): PyValue {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "List object cannot be static"
        }
        val result = PyTupleObject(emptyList())
        converted[obj.address] = result
        result.items = constructArrayContents(obj)
        return result
    }

    private fun convertDict(obj: InterpretedSymbolicPythonObject): PyValue {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "Input dict cannot be static"
        }
        val result = PyCompositeObject(PyIdentifier("builtins", "dict"), emptyList())
        converted[obj.address] = result
        if (obj.dictIsEmpty(state.ctx)) {
            return result
        }
        val dictItems = mutableListOf<Pair<PyValue, PyValue>>()
        val model = state.pyModel
        model.possibleRefKeys.forEach {
            val key = if (isStaticHeapRef(it)) {
                val type = state.memory.typeStreamOf(it).first()
                require(type is ConcretePythonType)
                InterpretedAllocatedOrStaticSymbolicPythonObject(it, type, state.typeSystem)
            } else {
                InterpretedInputSymbolicPythonObject(it, modelHolder, state.typeSystem)
            }
            if (obj.dictContainsRef(state.ctx, key)) {
                val convertedKey = convert(key)
                val value = obj.readDictRefElement(state.ctx, key, state.memory)
                val convertedValue = convert(value)
                dictItems.add(convertedKey to convertedValue)
            }
        }
        model.possibleIntKeys.forEach {
            if (obj.dictContainsInt(state.ctx, it)) {
                val value = obj.readDictIntElement(state.ctx, it, state.memory)
                val convertedKey = PyPrimitive(it.toString())
                val convertedValue = convert(value)
                dictItems.add(convertedKey to convertedValue)
            }
        }
        if (dictItems.isEmpty()) {
            val dummyObject = PyCompositeObject(PyIdentifier("builtins", "object"), emptyList())
            dictItems.add(dummyObject to PyPrimitive("None"))
        }
        result.dictItems = dictItems
        return result
    }

    private fun convertSet(obj: InterpretedSymbolicPythonObject): PyValue {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "Input set cannot be static"
        }
        if (obj.setIsEmpty(state.ctx)) {
            return PyCompositeObject(PyIdentifier("builtins", "set"), emptyList())
        }
        val items = mutableListOf<PyValue>()
        val model = state.pyModel
        model.possibleRefKeys.forEach {
            val key = if (isStaticHeapRef(it)) {
                val type = state.memory.typeStreamOf(it).first()
                require(type is ConcretePythonType)
                InterpretedAllocatedOrStaticSymbolicPythonObject(it, type, state.typeSystem)
            } else {
                InterpretedInputSymbolicPythonObject(it, modelHolder, state.typeSystem)
            }
            if (obj.setContainsRef(state.ctx, key)) {
                val convertedElem = convert(key)
                items.add(convertedElem)
            }
        }
        model.possibleIntKeys.forEach {
            if (obj.setContainsInt(state.ctx, it)) {
                items.add(PyPrimitive(it.toString()))
            }
        }
        if (items.isEmpty()) {
            val dummyObject = PyCompositeObject(PyIdentifier("builtins", "object"), emptyList())
            items.add(dummyObject)
        }
        val elemList = PyCompositeObject(PyIdentifier("builtins", "list"), emptyList())
        elemList.listItems = items
        return PyCompositeObject(PyIdentifier("builtins", "set"), listOf(elemList))
    }

    private fun convertFromDefaultConstructor(
        obj: InterpretedSymbolicPythonObject,
        type: ConcretePythonType,
    ): PyValue {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "Instance of type with default constructor cannot be static"
        }
        require(type.owner == state.typeSystem)
        val result = PyCompositeObject(
            PyIdentifier(
                "builtins",
                "object.__new__"
            ),
            listOf(type.id)
        )
        converted[obj.address] = result
        if (!ConcretePythonInterpreter.typeHasStandardDict(type.asObject)) {
            return result
        }
        val fields = mutableMapOf<String, PyValue>()
        state.preAllocatedObjects.listAllocatedStrs().forEach {
            val nameAddress = modelHolder.model.eval(it.address)
            require(isStaticHeapRef(nameAddress)) { "Symbolic string object must be static" }
            val nameSymbol =
                InterpretedAllocatedOrStaticSymbolicPythonObject(
                    nameAddress,
                    state.typeSystem.pythonStr,
                    state.typeSystem
                )
            if (obj.containsField(nameSymbol)) {
                addFieldToObject(obj, nameSymbol, it, type, fields)
            }
        }
        result.fieldDict = fields
        return result
    }

    private fun addFieldToObject(
        obj: InterpretedInputSymbolicPythonObject,
        nameSymbol: InterpretedSymbolicPythonObject,
        strObj: UninterpretedSymbolicPythonObject,
        type: ConcretePythonType,
        fields: MutableMap<String, PyValue>,
    ) {
        val str = state.preAllocatedObjects.concreteString(strObj)
            ?: error("Could not find string representation of ${strObj.address}")
        if (ConcretePythonInterpreter.typeLookup(type.asObject, str) == null) {
            val strRef = state.preAllocatedObjects.refOfString(str)
                ?: error("Could not find ref of $str")
            val namespace = ConcretePythonInterpreter.getNewNamespace()
            ConcretePythonInterpreter.addObjectToNamespace(namespace, strRef, "field")
            ConcretePythonInterpreter.concreteRun(namespace, "import keyword")
            val isValidName = ConcretePythonInterpreter.eval(
                namespace,
                "field.isidentifier() and not keyword.iskeyword(field)"
            )
            if (ConcretePythonInterpreter.getPythonObjectRepr(isValidName) == "True") {
                val symbolicValue = obj.getFieldValue(state.ctx, nameSymbol, state.memory)
                val value = convert(symbolicValue)
                fields[str] = value
            }
            ConcretePythonInterpreter.decref(namespace)
        }
    }

    private fun constructArrayContents(
        obj: InterpretedInputSymbolicPythonObject,
    ): List<PyValue> {
        val size = obj.readArrayLength(state.ctx) as? KInt32NumExpr ?: throw LengthOverflowException()
        if (size.value > MAX_INPUT_ARRAY_LENGTH) {
            throw LengthOverflowException()
        }
        return List(size.value) { index ->
            val indexExpr = state.ctx.mkSizeExpr(index) as KInterpretedValue<KIntSort>
            val elemInterpretedObject = obj.readArrayElement(indexExpr, state)
            convert(elemInterpretedObject)
        }
    }
}

class LengthOverflowException : RuntimeException()
