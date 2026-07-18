package org.usvm.ts.pbt.external

import org.jacodb.ets.model.EtsMethod
import org.usvm.ts.pbt.interpreter.VUndefined
import org.usvm.ts.pbt.interpreter.VValue
import java.nio.file.Path

data class ConcreteInputCase(
    val id: String,
    val methodId: String,
    val receiver: VValue = VUndefined,
    val arguments: List<VValue>,
    val producer: String,
    val metadata: Map<String, String> = emptyMap(),
    /** Canonical value-only key used to deduplicate inputs across producers. */
    val fingerprint: String = fingerprint(receiver, arguments),
) {
    companion object {
        fun fingerprint(receiver: VValue, arguments: List<VValue>): String =
            ExternalTestCorpusCodec.inputFingerprint(
                ExternalValueCodec.fromVValue(receiver),
                arguments.map(ExternalValueCodec::fromVValue),
            )
    }
}

data class ConcreteInputRejection(
    val id: String?,
    val reason: String,
)

data class ConcreteInputBatch(
    val producer: String,
    /** Cases for this method before value conversion (valid + rejected). */
    val imported: Int,
    val cases: List<ConcreteInputCase>,
    val rejections: List<ConcreteInputRejection> = emptyList(),
)

interface ConcreteInputProvider {
    val name: String
    fun inputsFor(method: EtsMethod): ConcreteInputBatch
}

/** ETC-backed provider used by the CLI and external tool adapters. */
class ExternalCorpusInputProvider private constructor(
    private val corpus: ExternalCorpusReadResult,
) : ConcreteInputProvider {
    override val name: String get() = corpus.producer

    override fun inputsFor(method: EtsMethod): ConcreteInputBatch {
        val target = stableMethodId(method)
        val matchingCases = corpus.cases.filter { it.methodId == target }
        val matchingDecodeRejections = corpus.rejections.filter { it.methodId == target }
        val cases = mutableListOf<ConcreteInputCase>()
        val rejections = matchingDecodeRejections.mapTo(mutableListOf()) {
            ConcreteInputRejection(it.id, it.reason)
        }

        for (case in matchingCases) {
            if (case.id.isBlank()) {
                rejections += ConcreteInputRejection(case.id, "case id is blank")
                continue
            }
            runCatching {
                val receiver = ExternalValueCodec.toVValue(case.receiver)
                val arguments = case.arguments.map(ExternalValueCodec::toVValue)
                ConcreteInputCase(
                    id = case.id,
                    methodId = case.methodId,
                    receiver = receiver,
                    arguments = arguments,
                    producer = corpus.producer,
                    metadata = case.metadata,
                )
            }.onSuccess(cases::add).onFailure { cause ->
                rejections += ConcreteInputRejection(
                    case.id,
                    cause.message ?: "${cause::class.simpleName} while decoding values",
                )
            }
        }

        return ConcreteInputBatch(
            producer = corpus.producer,
            imported = matchingCases.size + matchingDecodeRejections.size,
            cases = cases,
            rejections = rejections,
        )
    }

    companion object {
        fun fromPath(path: Path): ExternalCorpusInputProvider =
            ExternalCorpusInputProvider(ExternalTestCorpusCodec.read(path))

        fun fromCorpus(corpus: ExternalTestCorpus): ExternalCorpusInputProvider =
            ExternalCorpusInputProvider(
                ExternalCorpusReadResult(
                    schemaVersion = corpus.schemaVersion.also { version ->
                        require(version == EXTERNAL_TEST_CORPUS_SCHEMA_VERSION) {
                            "unsupported External Test Corpus schemaVersion $version; " +
                                "expected $EXTERNAL_TEST_CORPUS_SCHEMA_VERSION"
                        }
                    },
                    producer = corpus.producer,
                    cases = corpus.cases,
                    rejections = emptyList(),
                )
            )
    }
}

/** Small programmatic provider useful for adapters and unit tests. */
class ListConcreteInputProvider(
    private val producer: String,
    private val cases: List<ConcreteInputCase>,
) : ConcreteInputProvider {
    override val name: String get() = producer

    override fun inputsFor(method: EtsMethod): ConcreteInputBatch {
        val target = stableMethodId(method)
        val matching = cases.filter { it.methodId == target }
        return ConcreteInputBatch(producer, matching.size, matching)
    }
}
