package org.usvm.machine.results.observers

import org.usvm.python.model.PyInputModel

interface InputModelObserver {
    fun onInputModel(inputModel: PyInputModel)
}

object EmptyInputModelObserver : InputModelObserver {
    override fun onInputModel(inputModel: PyInputModel) = run {}
}
