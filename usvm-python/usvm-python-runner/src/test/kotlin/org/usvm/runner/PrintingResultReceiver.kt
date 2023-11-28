package org.usvm.runner

class PrintingResultReceiver: USVMPythonAnalysisResultReceiver() {
    var cnt: Int = 0
    override fun receivePickledInputValues(pickledTuple: String) {
        println(pickledTuple)
        cnt += 1
    }
}