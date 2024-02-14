package org.usvm.fuzzer.types

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.LocationType
import org.jacodb.api.ext.allSuperHierarchySequence
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

object JcClassTable {
    //TODO make in lazy
    var classes = mutableSetOf<JcClassOrInterface>()

    @OptIn(ExperimentalTime::class)
    fun initClasses(jcClasspath: JcClasspath) {
        measureTime {
            val locations = jcClasspath.locations
            for (location in locations) {
                val classNames =
                    if (location.type == LocationType.APP) {
                        location.classNames
                    } else {
                        location.classNames?.filter { it.startsWith("java.util") || it.startsWith("java.lang") || it.startsWith("java.reflect") }
                    } ?: listOf()
                classes.addAll(
                    classNames.mapNotNull { jcClasspath.findClassOrNull(it) }
                )
            }
        }.also { println("INIT DURATION = $it") }
    }

    @OptIn(ExperimentalTime::class)
    fun getRandomSubclassOf(superClasses: List<JcClassOrInterface>): JcClassOrInterface? = measureTimedValue {
        classes.shuffled().firstOrNull { jcClass ->
            if (jcClass.isInterface || jcClass.isAbstract || jcClass.isAnnotation) {
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
    }.also { println("TIME = ${it.duration} OF GETTING SUBCLASS OF ${superClasses.firstOrNull()?.name} res = ${it.value?.name}")}.value

    @OptIn(ExperimentalTime::class)
    fun getRandomTypeSuitableForBounds(lowerBounds: List<JcClassOrInterface>, upperBounds: List<JcClassOrInterface>, onlyClasses: Boolean) = measureTimedValue {
        classes.shuffled().firstOrNull { jcClass ->
            if (jcClass.outerClass != null && !jcClass.isStatic) return@firstOrNull false
            if (onlyClasses && (jcClass.isInterface || jcClass.isAbstract)) return@firstOrNull false
            if (jcClass.name == "java.lang.Object") return@firstOrNull false
            val isSuitableForUpperBounds =
                if (upperBounds.size == 1 && upperBounds.first().name == "java.lang.Object") {
                    true
                } else {
                    upperBounds.all { superClass -> jcClass.allSuperHierarchySequence.contains(superClass) }
                }
            if (!isSuitableForUpperBounds) {
                false
            } else {
                if (lowerBounds.size == 1 && lowerBounds.first() == jcClass) {
                    true
                } else {
                    lowerBounds.all { subClass -> subClass.allSuperHierarchySequence.contains(jcClass) }
                }
            }
        }
    }.also { println("TIME = ${it.duration} OF GETTING TYPE SUITABLE FOR BOUNDS $lowerBounds $upperBounds res = ${it.value?.name}")}.value
}