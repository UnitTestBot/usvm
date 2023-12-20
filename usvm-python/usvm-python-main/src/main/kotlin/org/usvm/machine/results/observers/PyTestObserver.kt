package org.usvm.machine.results.observers

import org.usvm.python.model.PyTest

abstract class PyTestObserver<PyObjectRepr> {
    abstract fun onPyTest(pyTest: PyTest<PyObjectRepr>)
}

class EmptyPyTestObserver<PyObjectRepr>: PyTestObserver<PyObjectRepr>() {
    override fun onPyTest(pyTest: PyTest<PyObjectRepr>) = run {}
}

class DefaultPyTestObserver<PyObjectRepr>: PyTestObserver<PyObjectRepr>() {
    val tests: MutableList<PyTest<PyObjectRepr>> = mutableListOf()
    override fun onPyTest(pyTest: PyTest<PyObjectRepr>) {
        tests.add(pyTest)
    }
}