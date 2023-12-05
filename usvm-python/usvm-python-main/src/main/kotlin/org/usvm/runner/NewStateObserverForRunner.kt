package org.usvm.runner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.usvm.machine.NewStateObserver
import org.usvm.machine.PythonExecutionState
import org.usvm.machine.rendering.StateSeedSender
import org.usvm.machine.saving.PickledObjectSaver

class NewStateObserverForRunner(
    communicator: PickledObjectCommunicator,
    private val scope: CoroutineScope
): NewStateObserver() {
    private val saver = PickledObjectSaver(communicator)
    private val seedSender = StateSeedSender(saver)
    private val sentData = mutableSetOf<String>()
    override fun onNewState(state: PythonExecutionState) {
        val data = seedSender.getData(state) ?: return
        if (data !in sentData) {
            sentData.add(data)
            scope.launch {
                seedSender.sendStateSeeds(data)
            }
        }
    }
}