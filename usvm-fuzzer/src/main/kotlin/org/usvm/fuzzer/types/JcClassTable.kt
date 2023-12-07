package org.usvm.fuzzer.types

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.ext.allSuperHierarchySequence
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object JcClassTable {
    //TODO make in lazy
    lateinit var classes: List<JcClassOrInterface>

    @OptIn(ExperimentalTime::class)
    fun initClasses(jcClasspath: JcClasspath) {
        measureTime {
            classes = jcClasspath.locations
                .flatMap { it.classNames ?: setOf() }
                //TODO REMOVE IT
                .filter { it.contains("java.") || it.contains("example.") }
                .mapNotNull { jcClasspath.findClassOrNull(it) }
        }.also { println("INIT DURATION = $it") }
    }

    @OptIn(ExperimentalTime::class)
    fun getRandomSubclassOf(superClasses: List<JcClassOrInterface>): JcClassOrInterface? = measureTimedValue {
        classes.shuffled().firstOrNull { jcClass ->
            if (jcClass.isInterface || jcClass.isAbstract) {
                false
            } else {
                if (superClasses.size == 1 && jcClass == superClasses.first()) return@firstOrNull true
                if (jcClass.outerClass != null && !jcClass.isStatic) return@firstOrNull false
                try {
                    superClasses.all { superClass -> jcClass.allSuperHierarchySequence.contains(superClass) }
                } catch (e: Throwable) {
                    false
                }
            }
        }
    }.also { println("TIME = ${it.duration}") }.value
}