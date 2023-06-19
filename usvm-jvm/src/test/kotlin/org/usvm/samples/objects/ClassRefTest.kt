@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import java.lang.Boolean
import kotlin.Array
import kotlin.Suppress
import kotlin.arrayOf

internal class ClassRefTest : JavaMethodTestRunner() {
    @Test
    fun testTakeBooleanClassRef() {
        checkExecutionMatches(
            ClassRef::takeBooleanClassRef,
            { _, r -> r == Boolean.TYPE }
        )
    }

    @Test
    fun testTakeClassRef() {
        checkExecutionMatches(
            ClassRef::takeClassRef,
            { _, r -> r == ClassRef::class.java }
        )
    }

    @Test
    fun testTakeClassRefFromParam() {
        checkExecutionMatches(
            ClassRef::takeClassRefFromParam,
            { _, classRef, _ -> classRef == null },
            { _, classRef, r -> r == classRef.javaClass }
        )
    }


    @Test
    fun testTakeArrayClassRef() {
        checkExecutionMatches(
            ClassRef::takeArrayClassRef,
            { _, r -> r == arrayOf<ClassRef>()::class.java }
        )
    }

    @Test
    fun testTwoDimArrayClassRef() {
        checkExecutionMatches(
            ClassRef::twoDimArrayClassRef,
            { _, r -> r == arrayOf<Array<ClassRef>>()::class.java }
        )
    }

    @Test
    fun testTwoDimArrayClassRefFromParam() {
        checkExecutionMatches(
            ClassRef::twoDimArrayClassRefFromParam,
            { _, array, _ -> array == null },
            { _, array, r -> r == array::class.java }
        )
    }

    @Test
    fun testTakeConstantClassRef() {
        checkExecutionMatches(
            ClassRef::takeConstantClassRef,
            { _, r -> r == ClassRef::class.java }
        )
    }

    @Test
    fun testEqualityOnClassRef() {
        checkExecutionMatches(
            ClassRef::equalityOnClassRef,
            { _, r -> r == true }, // we cannot find a way to have different class references
        )
    }

    @Test
    fun testEqualityOnStringClassRef() {
        checkExecutionMatches(
            ClassRef::equalityOnStringClassRef,
            { _, r -> r == true }, // we cannot find a way to have different class references
        )
    }

    @Test
    fun testEqualityOnArrayClassRef() {
        checkExecutionMatches(
            ClassRef::equalityOnArrayClassRef,
            { _, r -> r == true }, // we cannot find a way to have different class references
        )
    }

    @Test
    fun testTwoDimensionalArrayClassRef() {
        checkExecutionMatches(
            ClassRef::twoDimensionalArrayClassRef,
            { _, r -> r == true },
        )
    }

    @Test
    fun testEqualityOnGenericClassRef() {
        checkExecutionMatches(
            ClassRef::equalityOnGenericClassRef,
            { _, r -> r == true }, // we cannot find a way to have different class references
        )
    }
}