package org.usvm.ts.pbt.replay

import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.ts.pbt.external.ExternalTestCase
import org.usvm.ts.pbt.external.ExternalValueCodec
import org.usvm.ts.pbt.external.ExternalValueConversionException
import org.usvm.ts.pbt.external.stableBranchId
import org.usvm.ts.pbt.external.stableMethodId
import org.usvm.ts.pbt.interpreter.EtsConcreteInterpreter
import org.usvm.ts.pbt.interpreter.ExecutionListener
import org.usvm.ts.pbt.interpreter.ExecutionResult
import org.usvm.ts.pbt.interpreter.VValue
import java.util.IdentityHashMap

data class EtsIrReplayArguments(
    val receiver: VValue,
    val arguments: List<VValue>,
)

class ReplayInputRejectionException(
    val reasonCode: String,
    message: String,
) : IllegalArgumentException(message)

/**
 * The default decoder is deliberately loss-aware. Scene-aware callable,
 * constructor and alias materialization can be injected by A-INT without
 * changing ordering, accounting, or report generation in [ReplayPipeline].
 */
fun interface EtsIrReplayValueDecoder {
    fun decode(case: ExternalTestCase, scene: EtsScene): EtsIrReplayArguments
}

object StrictContractReplayValueDecoder : EtsIrReplayValueDecoder {
    override fun decode(case: ExternalTestCase, scene: EtsScene): EtsIrReplayArguments = try {
        EtsIrReplayArguments(
            receiver = ExternalValueCodec.toVValue(case.receiver),
            arguments = case.arguments.map(ExternalValueCodec::toVValue),
        )
    } catch (cause: ExternalValueConversionException) {
        throw ReplayInputRejectionException(
            ReplayReasonCode.INPUT_UNREPRESENTABLE,
            cause.message ?: "input is not representable by the concrete EtsIR interpreter",
        )
    }
}

/**
 * Production concrete executor. Scene loading stays outside this package; a
 * caller supplies the scene and every method whose branch callbacks should be
 * translated to stable IDs.
 */
class EtsIrReplayCaseExecutor(
    private val scene: EtsScene,
    methods: Collection<EtsMethod>,
    private val valueDecoder: EtsIrReplayValueDecoder = StrictContractReplayValueDecoder,
) : ReplayCaseExecutor {
    override val id: String = "etsir-concrete"
    override val isProduction: Boolean = true

    private val methodsById: Map<String, EtsMethod>
    private val branchesByIf: IdentityHashMap<EtsIfStmt, IdentityHashMap<EtsStmt, String>>

    init {
        val grouped = methods.groupBy(::stableMethodId)
        val collisions = grouped.filterValues { it.size > 1 }
        require(collisions.isEmpty()) {
            "stable method-id collisions in replay executor: ${collisions.keys.sorted().joinToString()}"
        }
        methodsById = grouped.mapValues { (_, values) -> values.single() }
        branchesByIf = IdentityHashMap()
        methods.forEach { method ->
            method.cfg.stmts.filterIsInstance<EtsIfStmt>().forEach { ifStmt ->
                val successors = IdentityHashMap<EtsStmt, String>()
                method.cfg.successors(ifStmt).forEach { successor ->
                    successors[successor] = stableBranchId(method, ifStmt, successor)
                }
                branchesByIf[ifStmt] = successors
            }
        }
    }

    override fun execute(case: ExternalTestCase): ReplayCaseExecution {
        val method = methodsById[case.methodId] ?: return ReplayCaseExecution.Rejected(
            ReplayReasonCode.METHOD_UNAVAILABLE,
            "method '${case.methodId}' is not loaded in the EtsIR replay runtime",
        )
        val decoded = try {
            valueDecoder.decode(case, scene)
        } catch (cause: ReplayInputRejectionException) {
            return ReplayCaseExecution.Rejected(cause.reasonCode, cause.message)
        } catch (cause: IllegalArgumentException) {
            return ReplayCaseExecution.Rejected(
                ReplayReasonCode.INPUT_UNREPRESENTABLE,
                cause.message ?: "input decoder rejected the case",
            )
        }

        val covered = linkedSetOf<String>()
        val listener = object : ExecutionListener {
            override fun onBranch(ifStmt: EtsIfStmt, taken: EtsStmt, condition: Boolean) {
                branchesByIf[ifStmt]?.get(taken)?.let(covered::add)
            }
        }
        val result = try {
            EtsConcreteInterpreter(scene).execute(
                method = method,
                thisValue = decoded.receiver,
                args = decoded.arguments,
                listener = listener,
            )
        } catch (cause: Exception) {
            return ReplayCaseExecution.Executed(
                coveredBranchIds = covered,
                reasonCode = ReplayReasonCode.EXECUTOR_FAILURE,
                detail = cause.message ?: cause::class.simpleName ?: "executor failure",
            )
        }

        return when (result) {
            is ExecutionResult.Returned -> ReplayCaseExecution.Executed(
                covered,
                ReplayReasonCode.REPLAY_RETURNED,
            )

            is ExecutionResult.Threw -> ReplayCaseExecution.Executed(
                covered,
                ReplayReasonCode.REPLAY_THREW,
            )

            is ExecutionResult.Unsupported -> ReplayCaseExecution.Executed(
                covered,
                ReplayReasonCode.REPLAY_UNSUPPORTED,
                result.reason,
            )

            is ExecutionResult.Diverged -> ReplayCaseExecution.Executed(
                covered,
                ReplayReasonCode.REPLAY_DIVERGED,
                result.reason,
            )
        }
    }
}
