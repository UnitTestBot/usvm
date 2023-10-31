package org.usvm.runner

class PrintingResultReceiver: USVMPythonAnalysisResultReceiver() {
    override fun receivePickledInputValues(pickledTuple: String) {
        println(pickledTuple)
    }
}