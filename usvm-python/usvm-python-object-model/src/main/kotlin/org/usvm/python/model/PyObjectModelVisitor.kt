package org.usvm.python.model

open class PyObjectModelVisitor {
    fun visit(obj: PyObjectModel) {
        obj.accept(this)
    }
    open fun visit(obj: PyPrimitive) = run {}
    open fun visit(obj: PyIdentifier) = run {}
    open fun visit(obj: PyMockObject) = run {}
    open fun visit(obj: PyCompositeObject) {
        visit(obj.constructor)
        obj.constructorArgs.forEach { visit(it) }
        obj.listItems?.let { it.forEach { item -> visit(item) } }
        obj.dictItems?.let {
            it.forEach { (key, value) ->
                visit(key)
                visit(value)
            }
        }
        obj.fieldDict?.let { it.values.forEach { item -> visit(item) } }
    }
    open fun visit(obj: PyTupleObject) {
        obj.items.forEach { visit(it) }
    }
}