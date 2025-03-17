package org.usvm.model

import org.jacodb.ets.model.EtsScene

class TsScene(
    val projectFiles: List<TsFile>,
    val sdkFiles: List<TsFile> = emptyList(),
    val etsScene: EtsScene? = null,
) {
    val projectClasses: List<TsClass>
        get() = projectFiles.flatMap { it.allClasses }

    val sdkClasses: List<TsClass>
        get() = sdkFiles.flatMap { it.allClasses }

    val projectAndSdkClasses: List<TsClass>
        get() = projectClasses + sdkClasses
}
