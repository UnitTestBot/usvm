package org.usvm.runner

import org.usvm.machine.results.observers.InputModelObserver
import org.usvm.machine.results.serialization.PickleArgsSerializer
import org.usvm.machine.symbolicobjects.rendering.PyValueRenderer
import org.usvm.python.model.PyInputModel

class InputModelObserverForRunner(
    private val communicator: PickledObjectCommunicator,
) : InputModelObserver {
    private val sentData = mutableSetOf<String>()
    override fun onInputModel(inputModel: PyInputModel) {
        val renderer = PyValueRenderer(useNoneInsteadOfMock = true)
        val objects = inputModel.inputArgs.map { renderer.convert(it) }
        val data = PickleArgsSerializer.serialize(objects) ?: return
        if (data !in sentData) {
            sentData.add(data)
            communicator.sendPickledInputs(data)
        }
    }
}
