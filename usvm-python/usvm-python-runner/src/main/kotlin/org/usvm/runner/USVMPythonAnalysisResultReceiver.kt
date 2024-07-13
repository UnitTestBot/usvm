package org.usvm.runner

interface USVMPythonAnalysisResultReceiver {
    fun receivePickledInputValues(pickledTuple: String)
}
