package org.usvm.util

import mu.KotlinLogging
import org.jacodb.ets.grpc.Server
import org.jacodb.ets.grpc.startArkAnalyzerServer
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

private val logger = KotlinLogging.logger {}

class SetupServer : BeforeAllCallback, AfterAllCallback {
    companion object {
        const val PORT = 42000
    }

    private lateinit var server: Server

    override fun beforeAll(context: ExtensionContext) {
        logger.info { "Setting up test environment..." }
        server = startArkAnalyzerServer(PORT)
        logger.info { "Done setting up test environment" }
    }

    override fun afterAll(context: ExtensionContext?) {
        logger.info { "Shutting down test environment..." }
        server.stop()
        logger.info { "Test environment shut down" }
    }
}
