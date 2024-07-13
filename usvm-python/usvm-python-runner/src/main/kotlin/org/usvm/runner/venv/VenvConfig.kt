package org.usvm.runner.venv

import java.io.File

data class VenvConfig(
    val basePath: File,
    val libPath: File,
    val binPath: File,
)
