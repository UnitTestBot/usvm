package org.usvm.python.model

sealed class PyObjectModel

class PyPrimitive(
    val repr: String
): PyObjectModel()

data class PyIdentifier(
    val module: String,
    val name: String
): PyObjectModel()

class PyCompositeObject(
    val constructor: PyIdentifier,
    val constructorArgs: List<PyObjectModel>,
    var listItems: List<PyObjectModel>? = null,
    var dictItems: List<Pair<PyObjectModel, PyObjectModel>>? = null,
    var fieldDict: Map<String, PyObjectModel>? = null
): PyObjectModel()

class PyTupleObject(var items: List<PyObjectModel>): PyObjectModel()

data class PyMockObject(val id: Int): PyObjectModel()