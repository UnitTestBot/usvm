package org.usvm.python.model

fun calculateNumberOfMocks(obj: PyObjectModel): Int {
    val visitor = object: PyObjectModelVisitor() {
        var result = 0
        override fun visit(obj: PyMockObject) {
            result += 1
            super.visit(obj)
        }
    }
    visitor.visit(obj)
    return visitor.result
}