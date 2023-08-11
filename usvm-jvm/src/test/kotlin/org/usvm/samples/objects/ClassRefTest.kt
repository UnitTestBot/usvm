@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
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
        checkDiscoveredProperties(
            ClassRef::takeBooleanClassRef,
            eq(1),
            { _, r -> r == Boolean.TYPE }
        )
    }

    @Test
    fun testTakeClassRef() {
        checkDiscoveredProperties(
            ClassRef::takeClassRef,
            eq(1),
            { _, r -> r == ClassRef::class.java }
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1]")
    fun testTakeClassRefFromParam() {
        checkDiscoveredProperties(
            ClassRef::takeClassRefFromParam,
            eq(2),
            { _, classRef, _ -> classRef == null },
            { _, classRef, r -> r == classRef.javaClass }
        )
    }


    @Test
    fun testTakeArrayClassRef() {
        checkDiscoveredProperties(
            ClassRef::takeArrayClassRef,
            eq(1),
            { _, r -> r == arrayOf<ClassRef>()::class.java }
        )
    }

    @Test
    fun testTwoDimArrayClassRef() {
        checkDiscoveredProperties(
            ClassRef::twoDimArrayClassRef,
            eq(1),
            { _, r -> r == arrayOf<Array<ClassRef>>()::class.java }
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [1]")
    fun testTwoDimArrayClassRefFromParam() {
        checkDiscoveredProperties(
            ClassRef::twoDimArrayClassRefFromParam,
            eq(2),
            { _, array, _ -> array == null },
            { _, array, r -> r == array::class.java }
        )
    }

    @Test
    fun testTakeConstantClassRef() {
        checkDiscoveredProperties(
            ClassRef::takeConstantClassRef,
            eq(1),
            { _, r -> r == ClassRef::class.java }
        )
    }

    @Test
    fun testEqualityOnClassRef() {
        checkDiscoveredProperties(
            ClassRef::equalityOnClassRef,
            eq(1),
            { _, r -> r == true }, // we cannot find a way to have different class references
        )
    }

    @Test
    fun testEqualityOnStringClassRef() {
        checkDiscoveredProperties(
            ClassRef::equalityOnStringClassRef,
            eq(1),
            { _, r -> r == true }, // we cannot find a way to have different class references
        )
    }

    @Test
    fun testEqualityOnArrayClassRef() {
        checkDiscoveredProperties(
            ClassRef::equalityOnArrayClassRef,
            eq(1),
            { _, r -> r != null && r }, // we cannot find a way to have different class references
        )
    }

    @Test
    fun testTwoDimensionalArrayClassRef() {
        checkDiscoveredProperties(
            ClassRef::twoDimensionalArrayClassRef,
            eq(1),
            { _, r -> r != null && r },
        )
    }

    @Test
    fun testEqualityOnGenericClassRef() {
        checkDiscoveredProperties(
            ClassRef::equalityOnGenericClassRef,
            eq(1),
            { _, r -> r != null && r }, // we cannot find a way to have different class references
        )
    }
}