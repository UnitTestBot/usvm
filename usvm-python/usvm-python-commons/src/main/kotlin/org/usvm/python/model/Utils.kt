package org.usvm.python.model

fun calculateNumberOfMocks(obj: PyValue): Int {
    val visitor = object : PyValueVisitor() {
        var result = 0
        override fun visit(obj: PyMockObject) {
            result += 1
            super.visit(obj)
        }
    }
    visitor.visit(obj)
    return visitor.result
}
