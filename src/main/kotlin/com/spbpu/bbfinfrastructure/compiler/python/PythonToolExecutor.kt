package com.spbpu.bbfinfrastructure.compiler.python

import com.spbpu.bbfinfrastructure.util.FuzzingConf
import java.io.BufferedReader
import java.io.InputStreamReader

open class PythonToolExecutor(private val commandToExec: String) {

    fun exec(absolutePathToTarget: String, ): String {
        val output = StringBuilder()
        val errorOutput = StringBuilder()
        val cmd = "export PATH=\"\$HOME/.pyenv/bin:\$PATH\";" +
                "eval \"\$(pyenv init --path)\";" +
                "eval \"\$(pyenv init -)\";" +
                "eval \"\$(pyenv virtualenv-init -)\";" +
                "pyenv activate ${FuzzingConf.pyEnvName}; cd $absolutePathToTarget; $commandToExec"
        with(ProcessBuilder()) {
            command("bash", "-c", cmd).start().let { process ->
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorStreamReader = BufferedReader(InputStreamReader(process.errorStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.appendLine(line)
                }
                while (errorStreamReader.readLine().also { line = it } != null) {
                    errorOutput.appendLine(line)
                }
                reader.close()
                process.waitFor()
            }
        }
        return output.toString()
    }

}