package org.usvm.runner

import mu.KLogging

private val logger = object : KLogging() {}.logger

class PrintingResultReceiver : USVMPythonAnalysisResultReceiver {
    var cnt: Int = 0
    override fun receivePickledInputValues(pickledTuple: String) {
        logger.info(pickledTuple)
        cnt += 1
    }
}
