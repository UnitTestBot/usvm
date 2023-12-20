package org.usvm.machine.results.observers

import org.usvm.python.model.PyInputModel

abstract class InputModelObserver {
    abstract fun onInputModel(inputModel: PyInputModel)
}

object EmptyInputModelObserver: InputModelObserver() {
    override fun onInputModel(inputModel: PyInputModel) = run {}
}