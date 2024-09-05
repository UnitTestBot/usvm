package com.spbpu.bbfinfrastructure.project

import com.spbpu.bbfinfrastructure.mutator.mutations.kotlin.Transformation.Companion.project
import com.spbpu.bbfinfrastructure.sarif.ToolsResultsSarifBuilder
import com.spbpu.bbfinfrastructure.sarif.ToolsResultsSarifBuilder.ResultArtifactLocation
import com.spbpu.bbfinfrastructure.util.FuzzingConf

data class Metadata(
    val sourceFileName: String,
    val initialCWEs: List<Int>,
    val originalUri: String?,
    var mutatedUri: String?,
    val mutationRegion: ToolsResultsSarifBuilder.ResultRegion? = null,
    var mutatedRegion: ToolsResultsSarifBuilder.ResultRegion? = mutationRegion
)  {

    fun getOriginalLocation(): ToolsResultsSarifBuilder.ResultLocation =
        ToolsResultsSarifBuilder.ResultLocation(
            ToolsResultsSarifBuilder.ResultPhysicalLocation(
                ResultArtifactLocation(originalUri!!),
                mutationRegion
            )
        )


    fun getMutatedLocation(): ToolsResultsSarifBuilder.ResultLocation =
        ToolsResultsSarifBuilder.ResultLocation(
            ToolsResultsSarifBuilder.ResultPhysicalLocation(
                ResultArtifactLocation(mutatedUri!!),
                mutatedRegion
            )
        )

    fun getDirOfOriginalFile(): String =
        FuzzingConf.pathToBenchmarkToFuzz + "/" + originalUri!!.substringBeforeLast('/')

    fun copy(): Metadata =
        Metadata(
            sourceFileName,
            initialCWEs,
            originalUri,
            mutatedUri,
            mutationRegion?.copy(),
            mutatedRegion?.copy()
        )

    companion object {
        fun createEmptyHeader(): Metadata = Metadata("", listOf(), null, null)
    }
}