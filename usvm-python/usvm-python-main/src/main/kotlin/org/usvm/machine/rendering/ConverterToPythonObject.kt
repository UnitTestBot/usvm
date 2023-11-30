package org.usvm.machine.rendering

import io.ksmt.expr.KInt32NumExpr
import org.usvm.*
import org.usvm.api.readArrayIndex
import org.usvm.api.typeStreamOf
import org.usvm.language.PythonCallable
import org.usvm.language.VirtualPythonObject
import org.usvm.language.types.*
import org.usvm.machine.UPythonContext
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.ConcretePythonInterpreter.emptyNamespace
import org.usvm.machine.interpreters.PythonNamespace
import org.usvm.machine.model.PyModel
import org.usvm.machine.symbolicobjects.*
import org.usvm.machine.utils.MAX_INPUT_ARRAY_LENGTH
import org.usvm.machine.utils.PyModelHolder
import org.usvm.memory.UMemory
import org.usvm.types.first

class ConverterToPythonObject(
    private val ctx: UPythonContext,
    private val typeSystem: PythonTypeSystem,
    val modelHolder: PyModelHolder,
    private val preallocatedObjects: PreallocatedObjects,
    private val memory: UMemory<PythonType, PythonCallable>,
    private val useNoneInsteadOfVirtual: Boolean = false
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

    fun convert(obj: InterpretedSymbolicPythonObject): PythonObject {
        if (obj is InterpretedInputSymbolicPythonObject)
            require(obj.modelHolder == modelHolder)
        require(!isAllocatedConcreteHeapRef(obj.address)) {
            "Cannot convert allocated objects"
        }
        val cached = constructedObjects[obj.address]
        if (cached != null) {
            ConcretePythonInterpreter.incref(cached)
            return cached
        }
        val result = when (val type = obj.getFirstType() ?: error("Type stream for interpreted object is empty")) {
            MockType -> constructVirtualObject(obj)
            typeSystem.pythonInt -> convertInt(obj)
            typeSystem.pythonBool -> convertBool(obj)
            typeSystem.pythonNoneType -> ConcretePythonInterpreter.eval(emptyNamespace, "None")
            typeSystem.pythonList -> convertList(obj)
            typeSystem.pythonTuple -> convertTuple(obj)
            typeSystem.pythonStr -> convertString(obj)
            typeSystem.pythonSlice -> convertSlice(obj)
            typeSystem.pythonFloat -> convertFloat(obj)
            typeSystem.pythonDict -> convertDict(obj)
            typeSystem.pythonSet -> convertSet(obj)
            else -> {
                if ((type as? ConcretePythonType)?.let { ConcretePythonInterpreter.typeHasStandardNew(it.asObject) } == true)
                    constructFromDefaultConstructor(obj, type)
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

    private fun convertSet(obj: InterpretedSymbolicPythonObject): PythonObject {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "Input set cannot be static"
        }
        return ConcretePythonInterpreter.eval(emptyNamespace, "set()")
    }

    private fun addEntryToDict(
        namespace: PythonNamespace,
        value: InterpretedSymbolicPythonObject,
    ) {
        val convertedValue = convert(value)
        ConcretePythonInterpreter.addObjectToNamespace(namespace, convertedValue, "value")
        ConcretePythonInterpreter.concreteRun(namespace, "x[key] = value", printErrorMsg = false)
    }

    private fun convertDict(obj: InterpretedSymbolicPythonObject): PythonObject {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "Input dict cannot be static"
        }
        if (obj.dictIsEmpty(ctx)) {
            return ConcretePythonInterpreter.eval(emptyNamespace, "dict()")
        }
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.concreteRun(namespace, "x = dict()")
        val result = ConcretePythonInterpreter.eval(namespace, "x")
        constructedObjects[obj.address] = result
        val model = modelHolder.model.uModel
        require(model is PyModel)
        var addedElems = 0
        model.possibleRefKeys.forEach {
            val key = if (isStaticHeapRef(it)) {
                val type = memory.typeStreamOf(it).first()
                require(type is ConcretePythonType)
                InterpretedAllocatedOrStaticSymbolicPythonObject(it, type, typeSystem)
            } else {
                InterpretedInputSymbolicPythonObject(it, modelHolder, typeSystem)
            }
            if (obj.dictContainsRef(key)) {
                val convertedKey = convert(key)
                ConcretePythonInterpreter.addObjectToNamespace(namespace, convertedKey, "key")
                val value = obj.readDictRefElement(ctx, key, memory)
                addedElems += 1
                addEntryToDict(namespace, value)
            }
        }
        model.possibleIntKeys.forEach {
            if (obj.dictContainsInt(ctx, it)) {
                ConcretePythonInterpreter.concreteRun(namespace, "key = $it")
                val value = obj.readDictIntElement(ctx, it, memory)
                addedElems += 1
                addEntryToDict(namespace, value)
            }
        }
        if (addedElems == 0) {
            ConcretePythonInterpreter.concreteRun(
                namespace,
                """
                elem = object()
                x[elem] = None
                """.trimIndent()
            )
        }
        return result.also {
            ConcretePythonInterpreter.incref(it)
            ConcretePythonInterpreter.decref(namespace)
        }
    }

    private fun convertFloat(obj: InterpretedSymbolicPythonObject): PythonObject {
        val cmd = when (val floatValue = obj.getFloatContent(ctx, memory)) {
            is FloatNan -> "float('nan')"
            is FloatPlusInfinity -> "float('inf')"
            is FloatMinusInfinity -> "float('-inf')"
            is FloatNormalValue -> floatValue.value.toString()
        }
        return ConcretePythonInterpreter.eval(emptyNamespace, cmd)
    }

    private fun convertString(obj: InterpretedSymbolicPythonObject): PythonObject {
        if (isStaticHeapRef(obj.address)) {
            val uninterpreted = UninterpretedSymbolicPythonObject(obj.address, typeSystem)
            val str = preallocatedObjects.concreteString(uninterpreted)
            val ref = str?.let { preallocatedObjects.refOfString(str) }
            if (ref != null)
                return ref
        }
        return ConcretePythonInterpreter.eval(emptyNamespace, "'${strNumber++}'")
    }

    private fun constructArrayContents(
        obj: InterpretedInputSymbolicPythonObject
    ): List<PythonObject> {
        val size = obj.readArrayLength(ctx) as? KInt32NumExpr ?: throw LengthOverflowException
        if (size.value > MAX_INPUT_ARRAY_LENGTH)
            throw LengthOverflowException
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
            val elemInterpretedObject =
                if (isStaticHeapRef(element)) {
                    val type = memory.typeStreamOf(element).first()
                    require(type is ConcretePythonType)
                    InterpretedAllocatedOrStaticSymbolicPythonObject(element, type, typeSystem)
                } else {
                    InterpretedInputSymbolicPythonObject(element, obj.modelHolder, typeSystem)
                }
            convert(elemInterpretedObject)
        }
    }

    private fun constructFromDefaultConstructor(
        obj: InterpretedSymbolicPythonObject,
        type: ConcretePythonType
    ): PythonObject {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "Instance of type with default constructor cannot be static"
        }
        require(type.owner == typeSystem)
        val result = ConcretePythonInterpreter.callStandardNew(type.asObject)
        constructedObjects[obj.address] = result
        if (ConcretePythonInterpreter.typeHasStandardDict(type.asObject)) {
            preallocatedObjects.listAllocatedStrs().forEach {
                val nameAddress = modelHolder.model.eval(it.address)
                require(isStaticHeapRef(nameAddress)) { "Symbolic string object must be static" }
                val nameSymbol = InterpretedAllocatedOrStaticSymbolicPythonObject(nameAddress, typeSystem.pythonStr, typeSystem)
                if (obj.containsField(nameSymbol)) {
                    val str = preallocatedObjects.concreteString(it)!!
                    if (ConcretePythonInterpreter.typeLookup(type.asObject, str) == null) {
                        val symbolicValue = obj.getFieldValue(ctx, nameSymbol, memory)
                        val value = convert(symbolicValue)
                        val strRef = preallocatedObjects.refOfString(str)!!
                        val namespace = ConcretePythonInterpreter.getNewNamespace()
                        ConcretePythonInterpreter.addObjectToNamespace(namespace, strRef, "field")
                        ConcretePythonInterpreter.concreteRun(namespace, "import keyword")
                        val isValidName = ConcretePythonInterpreter.eval(
                            namespace,
                            "field.isidentifier() and not keyword.iskeyword(field)"
                        )
                        if (ConcretePythonInterpreter.getPythonObjectRepr(isValidName) == "True") {
                            // println("Setting field: $str")
                            // val valueType = ConcretePythonInterpreter.getPythonObjectType(value)
                            // println("With value of type: ${ConcretePythonInterpreter.getNameOfPythonType(valueType)}")
                            // ConcretePythonInterpreter.incref(value)
                            // ConcretePythonInterpreter.incref(strRef)
                            ConcretePythonInterpreter.addObjectToNamespace(namespace, result, "obj")
                            ConcretePythonInterpreter.addObjectToNamespace(namespace, value, "value")
                            ConcretePythonInterpreter.concreteRun(
                                namespace,
                                "setattr(obj, field, value)",
                                printErrorMsg = true
                            )
                        }
                        ConcretePythonInterpreter.decref(namespace)
                    }
                }
            }
        }
        return result
    }

    private fun constructVirtualObject(obj: InterpretedSymbolicPythonObject): PythonObject {
        if (useNoneInsteadOfVirtual) {
            return ConcretePythonInterpreter.eval(emptyNamespace, "None")
        }
        require(obj is InterpretedInputSymbolicPythonObject) {
            "Virtual object cannot be static"
        }
        val default = forcedConcreteTypes[obj.address]?.let { defaultValueProvider.provide(it) }
        if (default != null)
            return default

        numberOfUsagesOfVirtualObjects += 1
        val virtual = VirtualPythonObject(obj)
        val result = ConcretePythonInterpreter.allocateVirtualObject(virtual)
        virtualObjects.add(virtual to result)
        return result
    }

    private fun convertInt(obj: InterpretedSymbolicPythonObject): PythonObject =
        ConcretePythonInterpreter.eval(emptyNamespace, obj.getIntContent(ctx, memory).toString())

    private fun convertBool(obj: InterpretedSymbolicPythonObject): PythonObject =
        when (obj.getBoolContent(ctx, memory)) {
            ctx.trueExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "True")
            ctx.falseExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "False")
            else -> error("Not reachable")
        }

    private fun convertList(obj: InterpretedSymbolicPythonObject): PythonObject {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "List object cannot be static"
        }
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

    private fun convertTuple(obj: InterpretedSymbolicPythonObject): PythonObject {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "Tuple object cannot be static"
        }
        val size = obj.readArrayLength(ctx) as? KInt32NumExpr ?: throw LengthOverflowException
        val resultTuple = ConcretePythonInterpreter.allocateTuple(size.value)
        constructedObjects[obj.address] = resultTuple
        val listOfPythonObjects = constructArrayContents(obj)
        listOfPythonObjects.forEachIndexed { index, pythonObject ->
            ConcretePythonInterpreter.setTupleElement(resultTuple, index, pythonObject)
        }
        return resultTuple
    }

    private fun convertSlice(obj: InterpretedSymbolicPythonObject): PythonObject {
        require(obj is InterpretedInputSymbolicPythonObject) {
            "Slice cannot be static"
        }
        val (start, stop, step) = obj.getSliceContent(ctx, typeSystem)
        val startStr = start?.toString() ?: "None"
        val stopStr = stop?.toString() ?: "None"
        val stepStr = step?.toString() ?: "None"
        return ConcretePythonInterpreter.eval(emptyNamespace, "slice($startStr, $stopStr, $stepStr)")
    }
}

object LengthOverflowException: Exception() {
    private fun readResolve(): Any = LengthOverflowException
}