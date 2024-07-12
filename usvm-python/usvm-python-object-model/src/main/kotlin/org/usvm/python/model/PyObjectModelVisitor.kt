package org.usvm.python.model

open class PyObjectModelVisitor {
    private val visited = mutableSetOf<PyObjectModel>()
    fun visit(obj: PyObjectModel) {
        obj.accept(this)
    }
    open fun visit(obj: PyPrimitive) {
        visited.add(obj)
    }
    open fun visit(obj: PyIdentifier) {
        visited.add(obj)
    }
    open fun visit(obj: PyMockObject) {
        visited.add(obj)
    }
    open fun visit(obj: PyCompositeObject) {
        visited.add(obj)
        if (obj.constructor !in visited)
            visit(obj.constructor)
        obj.constructorArgs.forEach {
            if (it !in visited)
                visit(it)
        }
        obj.listItems?.let {
            it.forEach { item ->
                if (item !in visited)
                    visit(item)
            }
        }
        obj.dictItems?.let {
            it.forEach { (key, value) ->
                if (key !in visited)
                    visit(key)
                if (value !in visited)
                    visit(value)
            }
        }
        obj.fieldDict?.let {
            it.values.forEach { item ->
                if (item !in visited)
                    visit(item)
            }
        }
    }
    open fun visit(obj: PyTupleObject) {
        visited.add(obj)
        obj.items.forEach {
            if (it !in visited)
                visit(it)
        }
    }
}