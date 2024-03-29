package com.spbpu.bbfinfrastructure.server

import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class FuzzServerInteract {


    private val host = "95.217.192.98"
    private val port = 22
    private val username = "stepanov"
    private val privateKeyPath = System.getenv("PRIVATE_KEY_PATH")
    private val privateKeyPassphrase = System.getenv("PRIVATE_KEY_PASS")

    private val jsch = JSch().also {
        it.addIdentity(privateKeyPath, privateKeyPassphrase)
    }


    fun execCommand(command: String) = interactWithServer<String, ChannelExec>("exec") { channel ->
        val buffer = ByteArray(1024)
        val output = StringBuilder()
        channel.setCommand(command)
        channel.connect()
        val inputStream = channel.inputStream
        while (true) {
            while (inputStream.available() > 0) {
                val read = inputStream.read(buffer)
                if (read < 0) break
                println(String(buffer, 0, read))
            }
            if (channel.isClosed) {
                if (inputStream.available() > 0) continue
                println("Exit status: ${channel.exitStatus}")
                break
            }
            Thread.sleep(1000)
        }
        "Command output:\n$output"
    }

    fun downloadFilesFromRemote(remoteToLocalPaths: Map<String, String>) {
        interactWithServer<String, ChannelSftp>("sftp") { channel ->
            channel.connect()
            for ((remoteFilePath, localFilePath) in remoteToLocalPaths) {
                val inputStream: InputStream = channel.get(remoteFilePath)
                val content = inputStream.bufferedReader().readText()
                if (localFilePath.count { it == '/' } > 1) {
                    File(localFilePath.substringBeforeLast('/')).mkdirs()
                }
                File(localFilePath).writeText(content)
            }
            "Files downloaded from remote successfully"
        }
    }

    fun downloadFilesToRemote(remoteToLocalPaths: Map<String, String>) {
        interactWithServer<String, ChannelSftp>("sftp") { channel ->
            channel.connect()
            for ((remoteFilePath, localFilePath) in remoteToLocalPaths) {
                val outputStream: OutputStream = channel.put(remoteFilePath)
                val content = File(localFilePath).readText()
                outputStream.write(content.toByteArray())
                outputStream.close()
            }
            "Files downloaded to remote successfully"
        }
    }


    @Suppress("UNCHECKED_CAST")
    private fun <T, S : Channel> interactWithServer(channelType: String, body: (channel: S) -> T): T? {
        val session = jsch.getSession(username, host, port)
        session.setConfig("PreferredAuthentications", "publickey")

        val config = java.util.Properties()
        config["StrictHostKeyChecking"] = "no"
        session.setConfig(config)
        session.connect()

        val channel = session.openChannel(channelType) as S
        val result: T?
        try {
            result = body(channel)
        } catch (e: Throwable) {
            println("Server interaction error: ${e.message}")
            return null
        } finally {
            channel.disconnect()
            session.disconnect()
        }
        return result
    }


}