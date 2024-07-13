package org.usvm.runner

import org.usvm.machine.PyState
import org.usvm.machine.model.PyModelHolder
import org.usvm.machine.results.observers.NewStateObserver
import org.usvm.machine.results.serialization.PickleArgsSerializer
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.machine.symbolicobjects.rendering.PyValueBuilder
import org.usvm.machine.symbolicobjects.rendering.PyValueRenderer

class NewStateObserverForRunner(
    private val communicator: PickledObjectCommunicator,
) : NewStateObserver {
    private val sentData = mutableSetOf<String>()
    override fun onNewState(state: PyState) {
        val modelHolder = PyModelHolder(state.pyModel)
        val builder = PyValueBuilder(state, modelHolder)
        val renderer = PyValueRenderer(useNoneInsteadOfMock = true)
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
