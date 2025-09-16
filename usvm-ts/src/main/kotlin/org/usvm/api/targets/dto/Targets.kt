package org.usvm.api.targets.dto

import org.jacodb.ets.dto.StmtDto

sealed interface TargetDto {
    val location: StmtDto

    class InitialPoint(override val location: StmtDto) : TargetDto
    class IntermediatePoint(override val location: StmtDto) : TargetDto
    class FinalPoint(override val location: StmtDto) : TargetDto
}
