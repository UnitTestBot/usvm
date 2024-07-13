package org.usvm.python.model

sealed class PyValue {
    abstract fun accept(visitor: PyValueVisitor)
}

class PyPrimitive(
    val repr: String,
) : PyValue() {
    override fun accept(visitor: PyValueVisitor) {
        visitor.visit(this)
    }
}

data class PyIdentifier(
    val module: String,
    val name: String,
) : PyValue() {
    override fun accept(visitor: PyValueVisitor) {
        visitor.visit(this)
    }
}

class PyCompositeObject(
    val constructor: PyIdentifier,
    val constructorArgs: List<PyValue>,
    var listItems: List<PyValue>? = null,
    var dictItems: List<Pair<PyValue, PyValue>>? = null,
    var fieldDict: Map<String, PyValue>? = null,
) : PyValue() {
    override fun accept(visitor: PyValueVisitor) {
        visitor.visit(this)
    }
}

class PyTupleObject(var items: List<PyValue>) : PyValue() {
    override fun accept(visitor: PyValueVisitor) {
        visitor.visit(this)
    }
}

data class PyMockObject(val id: Int) : PyValue() {
    override fun accept(visitor: PyValueVisitor) {
        visitor.visit(this)
    }
}
