package org.usvm.types

import org.jacodb.api.ByteCodeIndexer
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcDatabase
import org.jacodb.api.JcDatabasePersistence
import org.jacodb.api.JcFeature
import org.jacodb.api.JcSignal
import org.jacodb.api.RegisteredLocation
import org.jacodb.impl.fs.className
import org.jacodb.impl.storage.withoutAutoCommit
import org.jooq.DSLContext
import org.objectweb.asm.tree.ClassNode
import org.usvm.util.ApproximationPaths
import java.util.concurrent.ConcurrentHashMap

typealias ScoreCache<Result> = ConcurrentHashMap<Long, Result>

class ScorerIndexer<Result : Comparable<Result>>(
    private val persistence: JcDatabasePersistence,
    private val location: RegisteredLocation,
    private val cache: ScoreCache<Result>,
    private val scorer: (RegisteredLocation, ClassNode) -> Result,
    approximationPaths: ApproximationPaths = ApproximationPaths(),
) : ByteCodeIndexer {
    private val interner = persistence.symbolInterner

    private val bad: Boolean = approximationPaths.presentPaths.any { it in location.path }

    override fun index(classNode: ClassNode) {
        val clazzSymbolId = interner.findOrNew(classNode.name.className)
        if (bad) {
            @Suppress("UNCHECKED_CAST")
            cache[clazzSymbolId] = Double.NEGATIVE_INFINITY as Result
        } else {
            cache[clazzSymbolId] = scorer(location, classNode)
        }
    }

    override fun flush(jooq: DSLContext) {
        jooq.withoutAutoCommit { conn ->
            interner.flush(conn)
        }
    }

    fun getScore(name: String): Result? {
        val clazzSymbolId = interner.findOrNew(name)
        return cache[clazzSymbolId]
    }

    val allClassesSorted by lazy {
        cache.entries
            .sortedByDescending { it.value }
            .asSequence()
            .map { (id, result) -> result to persistence.findSymbolName(id) }
    }
}

class ClassScorer<Result : Comparable<Result>>(
    val key: Any,
    private val scorer: (RegisteredLocation, ClassNode) -> Result,
) : JcFeature<Any?, Any?> {
    private val indexers = ConcurrentHashMap<Long, ScorerIndexer<Result>>()

    override fun newIndexer(jcdb: JcDatabase, location: RegisteredLocation): ByteCodeIndexer =
        indexers.getOrPut(location.id) { ScorerIndexer(jcdb.persistence, location, ConcurrentHashMap(), scorer) }

    override fun onSignal(signal: JcSignal) {
    }

    override suspend fun query(classpath: JcClasspath, req: Any?): Sequence<Any?> {
        return emptySequence()
    }

    fun getScore(location: RegisteredLocation, name: String): Result? =
        indexers[location.id]?.getScore(name)

    fun sortedClasses(location: RegisteredLocation): Sequence<Pair<Result, String>> =
        indexers[location.id]?.allClassesSorted.orEmpty()
}