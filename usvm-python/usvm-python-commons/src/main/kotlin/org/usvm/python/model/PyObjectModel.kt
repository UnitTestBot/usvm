package org.usvm.python.model

sealed class PyObjectModel {
    abstract fun accept(visitor: PyObjectModelVisitor)
}

class PyPrimitive(
    val repr: String,
) : PyObjectModel() {
    override fun accept(visitor: PyObjectModelVisitor) {
        visitor.visit(this)
    }
}

data class PyIdentifier(
    val module: String,
    val name: String,
) : PyObjectModel() {
    override fun accept(visitor: PyObjectModelVisitor) {
        visitor.visit(this)
    }
}

class PyCompositeObject(
    val constructor: PyIdentifier,
    val constructorArgs: List<PyObjectModel>,
    var listItems: List<PyObjectModel>? = null,
    var dictItems: List<Pair<PyObjectModel, PyObjectModel>>? = null,
    var fieldDict: Map<String, PyObjectModel>? = null,
) : PyObjectModel() {
    override fun accept(visitor: PyObjectModelVisitor) {
        visitor.visit(this)
    }
}

class PyTupleObject(var items: List<PyObjectModel>) : PyObjectModel() {
    override fun accept(visitor: PyObjectModelVisitor) {
        visitor.visit(this)
    }
}

data class PyMockObject(val id: Int) : PyObjectModel() {
    override fun accept(visitor: PyObjectModelVisitor) {
        visitor.visit(this)
    }
}
