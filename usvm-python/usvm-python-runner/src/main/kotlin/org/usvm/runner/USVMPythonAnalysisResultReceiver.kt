package org.usvm.runner

abstract class USVMPythonAnalysisResultReceiver {
    abstract fun receivePickledInputValues(pickledTuple: String)
}