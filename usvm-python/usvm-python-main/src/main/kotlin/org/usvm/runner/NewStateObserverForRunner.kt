package org.usvm.runner

import org.usvm.machine.PyState
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.symbolicobjects.rendering.PyObjectModelBuilder
import org.usvm.machine.symbolicobjects.rendering.PyObjectRenderer
import org.usvm.machine.results.observers.NewStateObserver
import org.usvm.machine.results.serialization.PickleArgsSerializer
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject

class NewStateObserverForRunner(
    private val communicator: PickledObjectCommunicator
): NewStateObserver() {
    private val sentData = mutableSetOf<String>()
    override fun onNewState(state: PyState) {
        val modelHolder = PyModelHolder(state.pyModel)
        val builder = PyObjectModelBuilder(state, modelHolder)
        val renderer = PyObjectRenderer(useNoneInsteadOfMock = true)
        val interpreted = state.inputSymbols.map {
            interpretSymbolicPythonObject(modelHolder, state.memory, it)
        }
        val models = interpreted.map { builder.convert(it) }
        val objects = models.map { renderer.convert(it) }
        val data = PickleArgsSerializer.serialize(objects) ?: return
        if (data !in sentData) {
            sentData.add(data)
            communicator.sendPickledInputs(data)
        }
    }
}